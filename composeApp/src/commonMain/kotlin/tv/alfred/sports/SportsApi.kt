package tv.alfred.sports

import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.features.profiles.ProfileRepository
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * Alfred TV's sports side, on the phone: the same alfred-tv-api on the house
 * server (iptv/alfred-tv-api) the TVs use. Away from home it is reached through
 * Tailscale (Alfred advertises a route to 192.168.4.68), so the address is the
 * same everywhere.
 *
 * Unlike the TV app the phone never calls /api/report or /api/away: a channel
 * failing on cellular says nothing about the channel, and a report sends it to
 * the back of every TV's list for 15 minutes.
 */
object SportsApi {
    const val BASE = "http://192.168.4.68:34420"

    private val http by lazy { createApiHttpClient() }
    private val json = Json { ignoreUnknownKeys = true }

    /** Each Nuvio profile has its own sports favorites; the server numbers them like the TV (profile_index). */
    private val p: String
        get() {
            val active = ProfileRepository.state.value.activeProfile?.profileIndex ?: ProfileRepository.activeProfileId
            return "profile=$active"
        }

    data class Channel(
        val role: String,          // "best" or "backup"
        val name: String,
        val chno: Int,
        val url: String?,          // null: not in Threadfin's lineup right now
        val check: String?,        // the server's game check: "showing", "not_showing" (something else on), or null
    )

    data class Game(
        val id: String,
        val league: String,        // code: NFL, NCAAF, NHL...
        val leagueName: String,    // College Football
        val sport: String,         // Football
        val title: String,
        val away: String,
        val home: String,
        val start: Instant,
        val stop: Instant,
        val status: String,        // live, soon, later; final or over once ESPN has it finished
        val channels: List<Channel>,
        val favorites: List<String>,   // which favorites it belongs to (team, league, sport names)
        val score: Score?,             // from ESPN's scoreboard; null for sports it has no board for
        val awayFlag: String?,         // country flag (full URL on the house server) for national teams
        val homeFlag: String?,
        val awayLogo: String?,         // team logo when the scoreboard has no match (CFL, a college game)
        val homeLogo: String?,
        val badge: String,             // the sport's emoji: a golf round or a race has no logos
    ) {
        val playable get() = channels.any { it.url != null }
        val hasTeams get() = away.isNotBlank() && home.isNotBlank()
        val isLive get() = status == "live"
        val displayTitle get() = if (hasTeams) "$away @ $home" else title
    }

    data class Side(
        val abbr: String,
        val name: String,          // "Broncos"
        val score: String?,
        val logo: String?,         // full URL on the house server
        val record: String?,       // "2-2"
        val possession: Boolean,
        val winner: Boolean,
    )

    data class Score(
        val state: String,         // pre, in, post
        val clock: String,         // "Q3 5:21", "Top 5th", "Halftime", "Final/OT"
        val away: Side,
        val home: Side,
        val downLong: String?,     // "3rd & 7 at SF 35"
        val redZone: Boolean,
        val outs: Int?,            // baseball
        val count: String?,        // "2-1"
        val bases: List<Boolean>,  // first, second, third
        val lastPlay: String?,
    ) {
        val live get() = state == "in"
        val final get() = state == "post"
        val hasScore get() = state != "pre"
    }

    /** kind is the server's: "teams" (a team name), "leagues" (a code), "sports" (a sport name). */
    data class Favorite(val kind: String, val name: String, val label: String)

    data class Favorites(val items: List<Favorite>) {
        fun has(kind: String, name: String) = items.any { it.kind == kind && it.name.equals(name, ignoreCase = true) }
    }

    /** A team, league or sport that can be made a favorite (from a search). */
    data class Pick(val kind: String, val name: String, val label: String, val detail: String, val favorite: Boolean)

    class ApiError(message: String, val code: Int) : Exception(message)

    /** This week's games (live first), optionally filtered by team, league, sport or title words. */
    suspend fun games(query: String? = null): List<Game> {
        val q = query?.trim().orEmpty()
        val a = get("/api/games?when=week&$p" + if (q.isNotEmpty()) "&q=" + q.encodeURLParameter() else "")
        return withContext(Dispatchers.Default) { (a as JsonArray).map { parseGame(it as JsonObject) } }
    }

    /** The game and its playable channels, best first. */
    suspend fun play(id: String): Pair<Game, List<Channel>> {
        val o = get("/api/play/${id.encodeURLParameter()}?$p") as JsonObject
        return parseGame(o.obj("game")!!) to o.arr("streams").map { parseChannel(it as JsonObject) }
    }

    suspend fun favorites(): Favorites = parseFavorites(get("/api/favorites?$p") as JsonObject)

    /** Add the favorite, or remove it if it is one already. */
    suspend fun toggleFavorite(kind: String, name: String): Favorites {
        val body = buildJsonObject {
            put("kind", kind)
            put("name", name)
        }
        return parseFavorites(post("/api/favorites/toggle?$p", body) as JsonObject)
    }

    /** Teams, leagues and sports matching q, for adding favorites. */
    suspend fun search(q: String): List<Pick> {
        val o = get("/api/catalog?$p&q=" + q.trim().encodeURLParameter()) as JsonObject
        val out = mutableListOf<Pick>()
        o.arr("teams").forEach { e ->
            val t = e as JsonObject
            val name = t.str("name") ?: return@forEach
            out += Pick("teams", name, name, t.strings("leagues").joinToString(", "), t.bool("favorite"))
        }
        o.arr("leagues").forEach { e ->
            val l = e as JsonObject
            val code = l.str("code") ?: return@forEach
            out += Pick("leagues", code, l.str("name") ?: code, l.str("sport").orEmpty(), l.bool("favorite"))
        }
        o.arr("sports").forEach { e ->
            val s = e as JsonObject
            val name = s.str("name") ?: return@forEach
            out += Pick("sports", name, name, "Sport", s.bool("favorite"))
        }
        return out
    }

    private suspend fun get(path: String): JsonElement = read(http.get(BASE + path))

    private suspend fun post(path: String, body: JsonObject): JsonElement =
        read(http.post(BASE + path) { setBody(TextContent(body.toString(), ContentType.Application.Json)) })

    private suspend fun read(r: HttpResponse): JsonElement {
        val text = r.bodyAsText()
        if (!r.status.isSuccess()) {
            // FastAPI errors are {"detail": "..."}: show that sentence as-is.
            val detail = runCatching { (json.parseToJsonElement(text) as JsonObject).str("detail") }.getOrNull()
            throw ApiError(detail ?: "Sports server answered ${r.status.value}", r.status.value)
        }
        return withContext(Dispatchers.Default) { json.parseToJsonElement(text) }
    }

    // A missing key, a JSON null and an empty string all read as null.
    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false

    private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject

    private fun JsonObject.arr(k: String): JsonArray = this[k] as? JsonArray ?: JsonArray(emptyList())

    private fun JsonObject.strings(k: String): List<String> =
        arr(k).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    /** Logos and flags come as paths on the house server. */
    private fun full(url: String?) = url?.let { if (it.startsWith("/")) BASE + it else it }

    private fun parseFavorites(o: JsonObject): Favorites {
        val names = o.obj("league_names")
        return Favorites(
            o.strings("teams").map { Favorite("teams", it, it) } +
                o.strings("leagues").map { Favorite("leagues", it, names?.str(it) ?: it) } +
                o.strings("sports").map { Favorite("sports", it, it) }
        )
    }

    private fun parseChannel(o: JsonObject) = Channel(
        role = o.str("role").orEmpty(),
        name = o.str("name").orEmpty(),
        chno = o.int("chno") ?: 0,
        url = o.str("url"),
        check = o.str("check"),
    )

    private fun parseSide(o: JsonObject) = Side(
        abbr = o.str("abbr").orEmpty(),
        name = o.str("name").orEmpty(),
        score = o.str("score"),
        logo = full(o.str("logo")),
        record = o.str("record"),
        possession = o.bool("possession"),
        winner = o.bool("winner"),
    )

    private fun parseScore(o: JsonObject?): Score? {
        if (o == null) return null
        return Score(
            state = o.str("state") ?: "pre",
            clock = o.str("clock").orEmpty(),
            away = parseSide(o.obj("away") ?: return null),
            home = parseSide(o.obj("home") ?: return null),
            downLong = o.str("down_long"),
            redZone = o.bool("red_zone"),
            outs = o.int("outs"),
            count = o.str("count"),
            bases = o.arr("bases").map { (it as? JsonPrimitive)?.booleanOrNull ?: false },
            lastPlay = o.str("last_play"),
        )
    }

    private fun parseGame(o: JsonObject): Game {
        val flags = o.obj("flags")
        val logos = o.obj("logos")
        return Game(
            id = o.str("id").orEmpty(),
            league = o.str("league").orEmpty(),
            leagueName = o.str("league_name") ?: o.str("league").orEmpty(),
            sport = o.str("sport") ?: "Other",
            title = o.str("title").orEmpty(),
            away = o.str("away").orEmpty(),
            home = o.str("home").orEmpty(),
            start = Instant.parse(o.str("start")!!),
            stop = Instant.parse(o.str("stop")!!),
            status = o.str("status").orEmpty(),
            channels = o.arr("channels").map { parseChannel(it as JsonObject) },
            favorites = o.strings("favorites"),
            score = runCatching { parseScore(o.obj("score")) }.getOrNull(),
            awayFlag = full(flags?.str("away")),
            homeFlag = full(flags?.str("home")),
            awayLogo = full(logos?.str("away")),
            homeLogo = full(logos?.str("home")),
            badge = o.str("badge")?.ifBlank { null } ?: "🏆",
        )
    }
}
