package tv.alfred.sports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the page is narrowed to: a favorite, or a sport or league picked from Browse. */
internal data class SportsFilter(val kind: String, val name: String, val label: String) {
    fun matches(g: SportsApi.Game) = when (kind) {
        "teams" -> g.away.equals(name, true) || g.home.equals(name, true) || g.favorites.any { it.equals(name, true) }
        "leagues" -> g.league.equals(name, true)
        "sports" -> g.sport.equals(name, true)
        else -> false
    }
}

/** The sports page's state; lives as long as the page. */
internal class SportsModel(private val scope: CoroutineScope) {
    var games by mutableStateOf<List<SportsApi.Game>>(emptyList())   // this week
        private set
    var favorites by mutableStateOf(SportsApi.Favorites(emptyList()))
        private set
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<SportsApi.Game>>(emptyList())  // games matching the query
        private set
    var picks by mutableStateOf<List<SportsApi.Pick>>(emptyList())    // teams/leagues/sports matching it
        private set
    var filter by mutableStateOf<SportsFilter?>(null)
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var searchJob: Job? = null

    suspend fun refresh() {
        try {
            favorites = SportsApi.favorites()
            games = SportsApi.games()
            if (query.isNotBlank()) results = SportsApi.games(query)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = unreachable(e)
        } finally {
            loading = false
        }
    }

    /** Another profile, other favorites. */
    fun resetForProfile() {
        filter = null
        favorites = SportsApi.Favorites(emptyList())
    }

    fun onQuery(q: String) {
        query = q
        searchJob?.cancel()
        if (q.isBlank()) {
            results = emptyList()
            picks = emptyList()
            return
        }
        searchJob = scope.launch {
            delay(300)          // wait for typing to pause
            try {
                results = SportsApi.games(q)
                picks = SportsApi.search(q)
                error = null
            } catch (e: CancellationException) {
                throw e          // a newer keystroke replaced this search
            } catch (e: Exception) {
                error = unreachable(e)
            }
        }
    }

    fun clearQuery() = onQuery("")

    fun toggleFavorite(kind: String, name: String, label: String) {
        scope.launch {
            try {
                val wasFavorite = favorites.has(kind, name)
                favorites = SportsApi.toggleFavorite(kind, name)
                NuvioToastController.show(if (wasFavorite) "Removed $label from favorites" else "★ Added $label to favorites")
                if (wasFavorite && filter?.kind == kind && filter?.name.equals(name, true)) filter = null
                games = SportsApi.games()
                if (query.isNotBlank()) {
                    results = SportsApi.games(query)
                    picks = SportsApi.search(query)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NuvioToastController.show("Couldn't change favorites: ${e.message ?: "no connection"}")
            }
        }
    }

    companion object {
        /** The server's own words when it answered; otherwise the likely reason away from home. */
        fun unreachable(e: Exception): String =
            if (e is SportsApi.ApiError) e.message ?: "The sports server had a problem"
            else "Can't reach Alfred. Away from home? Turn on Tailscale."
    }
}

/** The page's model, refreshed while it is on screen: every 20 s with a game on, otherwise every minute. */
@Composable
internal fun rememberSportsModel(): SportsModel {
    val scope = rememberCoroutineScope()
    val model = remember { SportsModel(scope) }
    val profiles by ProfileRepository.state.collectAsStateWithLifecycle()
    val profileIndex = profiles.activeProfile?.profileIndex ?: ProfileRepository.activeProfileId
    var lastProfile by remember { mutableStateOf(profileIndex) }
    LaunchedEffect(profileIndex) {
        if (profileIndex != lastProfile) {
            lastProfile = profileIndex
            model.resetForProfile()
            if (model.query.isNotBlank()) model.onQuery(model.query)
        }
        while (true) {
            model.refresh()
            delay(if (model.games.any { it.isLive }) 20_000 else 60_000)
        }
    }
    return model
}
