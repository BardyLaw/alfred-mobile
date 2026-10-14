package tv.alfred.sports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvio

internal val Gold = Color(0xFFFFC107)
internal val LiveRed = Color(0xFFE53935)

/** A pill with an optional second line; long-press for the secondary action (removing a favorite). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SportsChip(
    text: String,
    onClick: () -> Unit,
    detail: String? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.nuvio.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .clip(shape)
            .background(if (selected) Gold.copy(alpha = 0.14f) else colors.surfaceCard)
            .border(1.dp, if (selected) Gold.copy(alpha = 0.6f) else colors.borderDefault, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text, color = if (selected) Gold else colors.textPrimary, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (detail != null) {
            Text(detail, color = colors.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One game: league, LIVE/clock or start time, both teams with logos and the score, and the channel or situation. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GameCard(
    g: SportsApi.Game,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier.width(300.dp).height(150.dp),
) {
    val colors = MaterialTheme.nuvio.colors
    val shape = RoundedCornerShape(14.dp)
    val best = g.channels.firstOrNull { it.url != null }
    val isFav = g.favorites.isNotEmpty()
    val sc = g.score
    Surface(
        modifier = modifier
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        color = colors.surfaceCard,
        border = if (isFav) BorderStroke(1.dp, Gold.copy(alpha = 0.5f)) else null,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (isFav) Text("★ ", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    g.leagueName, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                StatusTag(g)
            }
            Spacer(Modifier.height(6.dp))
            if (sc != null && g.hasTeams) {
                TeamLine(g.away, sc.away, sc, football = g.sport == "Football", flag = g.awayFlag)
                Spacer(Modifier.height(2.dp))
                TeamLine(g.home, sc.home, sc, football = g.sport == "Football", flag = g.homeFlag)
            } else if (g.hasTeams) {
                MarkLine(g.away, g.awayLogo, g.awayFlag, g.badge)
                Spacer(Modifier.height(2.dp))
                MarkLine(g.home, g.homeLogo, g.homeFlag, g.badge)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SportBadge(g.badge, Modifier.size(38.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        g.title, color = colors.textPrimary, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val situation = sc?.takeIf { it.live }?.let { situationText(it) }
            if (situation != null) {
                Text(
                    situation, color = if (sc.redZone) LiveRed else Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    when {
                        best == null -> "Channel comes on nearer the start"
                        g.channels.count { it.url != null } > 1 -> "${best.name}  ·  backup ready"
                        else -> best.name
                    },
                    color = colors.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** LIVE + the game clock, Final, or the start time. */
@Composable
private fun StatusTag(g: SportsApi.Game) {
    val colors = MaterialTheme.nuvio.colors
    val sc = g.score
    when {
        g.status == "final" || sc?.final == true ->
            Text(sc?.clock?.ifBlank { null } ?: "Final", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        g.isLive -> Row(verticalAlignment = Alignment.CenterVertically) {
            if (sc?.live == true && sc.clock.isNotBlank()) {
                Text(sc.clock, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
            }
            Box(Modifier.background(LiveRed, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        SportsTime.isToday(g) -> Text(SportsTime.time(g.start), color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        else -> Text(SportsTime.dayTime(g.start), color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Logo, team, who has the ball, and the score. Before the start: the record instead of a score. */
@Composable
private fun TeamLine(team: String, side: SportsApi.Side, sc: SportsApi.Score, football: Boolean, flag: String?) {
    val colors = MaterialTheme.nuvio.colors
    val dim = sc.final && !side.winner && (sc.away.winner || sc.home.winner)
    val text = if (dim) colors.textSecondary else colors.textPrimary
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(side, Modifier.size(26.dp), flag)
        Spacer(Modifier.width(8.dp))
        Text(
            team, color = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        if (side.possession) {
            Spacer(Modifier.width(4.dp))
            Text(if (football) "🏈" else "●", color = Gold, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        if (sc.hasScore) {
            Text(side.score ?: "0", color = text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        } else if (side.record != null) {
            Text(side.record, color = colors.textSecondary, fontSize = 12.sp)
        }
    }
}

/** A team the scoreboard doesn't know (a CFL game, a national team before kickoff): its logo
 *  or flag and name; the sport's emoji when there's neither. */
@Composable
private fun MarkLine(team: String, logo: String?, flag: String?, badge: String) {
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        GameMark(team, logo, flag, badge, Modifier.size(26.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            team, color = MaterialTheme.nuvio.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A team logo, a flag, or the sport's emoji: whichever the game has. */
@Composable
private fun GameMark(team: String, logo: String?, flag: String?, badge: String, modifier: Modifier) {
    when {
        logo != null -> AsyncImage(model = logo, contentDescription = team, modifier = modifier)
        flag != null -> Flag(flag, team, modifier)
        else -> SportBadge(badge, modifier)
    }
}

/** The sport's emoji in a round chip: the picture for a golf round, a race, or a team with no logo. */
@Composable
private fun SportBadge(badge: String, modifier: Modifier) {
    BoxWithConstraints(modifier.background(MaterialTheme.nuvio.colors.surfaceElevated, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
        Text(badge, fontSize = (maxWidth.value * 0.5f).sp, maxLines = 1)
    }
}

/** The flag, fitted inside the logo's square (flags are 5:3). */
@Composable
private fun Flag(url: String, country: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        AsyncImage(model = url, contentDescription = country, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)))
    }
}

@Composable
private fun TeamLogo(side: SportsApi.Side, modifier: Modifier, flag: String? = null) {
    val colors = MaterialTheme.nuvio.colors
    when {
        side.logo != null -> AsyncImage(model = side.logo, contentDescription = side.name, modifier = modifier)
        flag != null -> Flag(flag, side.name, modifier)
        else -> Box(modifier.background(colors.surfaceElevated, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            Text(side.abbr.take(3), color = colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** "3rd & 7 at SF 35 · Red zone", "2 outs · 2-1 · On 1st & 3rd", or the last play. */
internal fun situationText(sc: SportsApi.Score): String? {
    sc.downLong?.let { return if (sc.redZone) "$it  ·  Red zone" else it }
    sc.outs?.let { outs ->
        val on = listOf("1st", "2nd", "3rd").filterIndexed { i, _ -> sc.bases.getOrElse(i) { false } }
        val bases = when (on.size) { 0 -> "Bases empty"; 3 -> "Bases loaded"; else -> "On " + on.joinToString(" & ") }
        return listOfNotNull("$outs out" + if (outs == 1) "" else "s", sc.count, bases).joinToString("  ·  ")
    }
    return sc.lastPlay
}

/** Long-press on a game: favorite either team, its league, or its sport. */
@Composable
internal fun FavoriteDialog(g: SportsApi.Game, model: SportsModel, onDismiss: () -> Unit) {
    val colors = MaterialTheme.nuvio.colors
    val options = buildList {
        if (g.away.isNotBlank()) add(SportsFilter("teams", g.away, g.away))
        if (g.home.isNotBlank()) add(SportsFilter("teams", g.home, g.home))
        add(SportsFilter("leagues", g.league, g.leagueName))
        if (g.sport != g.leagueName) add(SportsFilter("sports", g.sport, g.sport))
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceDialog, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Favorites", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(g.displayTitle, color = colors.textSecondary, fontSize = 14.sp)
            options.forEach { f ->
                val fav = model.favorites.has(f.kind, f.name)
                SportsChip(
                    text = (if (fav) "★ " else "☆ ") + f.label,
                    detail = when (f.kind) { "teams" -> "Team"; "leagues" -> "League"; else -> "Sport" } +
                        if (fav) " · in favorites, tap to remove" else " · tap to add",
                    selected = fav,
                    onClick = { model.toggleFavorite(f.kind, f.name, f.label) },
                )
            }
            SportsChip(text = "Done", onClick = onDismiss)
        }
    }
}
