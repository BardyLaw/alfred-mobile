package tv.alfred.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio

private data class Section(
    val title: String,
    val games: List<SportsApi.Game>,
    val key: String = title,
    val empty: String? = null,
    val gold: Boolean = false,
)

/**
 * The phone's version of the TV's Sports page: a search box, your favorites and Browse
 * (tap one to narrow the page to it), then rows -- live now, each favorite's games this
 * week, and today's games by sport. Tap a game to watch it; hold it to favorite its
 * teams, league or sport.
 */
@Composable
internal fun SportsScreen(onBack: () -> Unit, onPlay: (String) -> Unit) {
    val model = rememberSportsModel()
    val colors = MaterialTheme.nuvio.colors
    var menuFor by remember { mutableStateOf<SportsApi.Game?>(null) }

    PlatformBackHandler(enabled = model.filter != null || model.query.isNotEmpty()) {
        if (model.filter != null) model.filter = null else model.clearQuery()
    }

    val filter = model.filter
    val sections: List<Section> = when {
        model.query.isNotBlank() -> byWhen(model.results)
        filter != null -> byWhen(model.games.filter(filter::matches))
        else -> homeSections(model.games, model.favorites)
    }
    val live = model.games.count { it.isLive }
    val today = model.games.count { SportsTime.isToday(it) }

    NuvioScreen {
        stickyHeader {
            NuvioScreenHeader(title = filter?.label ?: "Sports", onBack = {
                if (filter != null) model.filter = null else onBack()
            })
        }

        item(key = "summary") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (model.loading) "Loading games…" else "$live live · $today today · ${model.games.size} this week",
                    color = colors.textSecondary, fontSize = 14.sp,
                )
                NuvioInputField(
                    value = model.query,
                    onValueChange = model::onQuery,
                    placeholder = "Search a team, league or sport",
                )
            }
        }

        val err = model.error
        if (err != null) item(key = "error") { Text(err, color = colors.warning, fontSize = 15.sp) }

        when {
            model.query.isNotBlank() -> {
                if (model.picks.isNotEmpty()) chipRow("picks", "Add to favorites — tap to add or remove") {
                    items(model.picks, key = { "${it.kind}:${it.name}" }) { p ->
                        SportsChip(
                            text = (if (p.favorite) "★ " else "☆ ") + p.label,
                            detail = p.detail.ifBlank { null },
                            selected = p.favorite,
                            onClick = { model.toggleFavorite(p.kind, p.name, p.label) },
                        )
                    }
                }
                if (sections.isEmpty() && !model.loading) item(key = "none") {
                    Text("No games this week match “${model.query}”.", color = colors.textSecondary, fontSize = 15.sp)
                }
            }
            filter != null -> {
                val fav = model.favorites.has(filter.kind, filter.name)
                chipRow("filter", null) {
                    item {
                        SportsChip(
                            text = if (fav) "★ In favorites" else "☆ Add ${filter.label} to favorites",
                            selected = fav,
                            onClick = { model.toggleFavorite(filter.kind, filter.name, filter.label) },
                        )
                    }
                    item { SportsChip(text = "Show everything", onClick = { model.filter = null }) }
                }
                if (sections.isEmpty() && !model.loading) item(key = "none") {
                    Text("No ${filter.label} games this week.", color = colors.textSecondary, fontSize = 15.sp)
                }
            }
            else -> {
                if (model.favorites.items.isEmpty()) {
                    if (!model.loading && err == null) item(key = "favorites") {
                        Text(
                            "No favorites yet. Search a team, league or sport — Broncos, NFL, Hockey — and tap it. " +
                                "Or hold a game.",
                            color = colors.textMuted, fontSize = 14.sp,
                        )
                    }
                } else {
                    chipRow("favorites", "Favorites — tap to show, hold to remove") {
                        items(model.favorites.items, key = { "${it.kind}:${it.name}" }) { f ->
                            val count = model.games.count { SportsFilter(f.kind, f.name, f.label).matches(it) }
                            SportsChip(
                                text = "★ ${f.label}",
                                detail = if (count == 0) "no games this week" else "$count this week",
                                selected = true,
                                onClick = { model.filter = SportsFilter(f.kind, f.name, f.label) },
                                onLongClick = { model.toggleFavorite(f.kind, f.name, f.label) },
                            )
                        }
                    }
                }
                val browse = browseChips(model.games)
                if (browse.isNotEmpty()) chipRow("browse", "Browse") {
                    items(browse, key = { "${it.first.kind}:${it.first.name}" }) { (f, n) ->
                        SportsChip(
                            text = f.label, detail = "$n this week",
                            selected = model.favorites.has(f.kind, f.name),
                            onClick = { model.filter = f },
                            onLongClick = { model.toggleFavorite(f.kind, f.name, f.label) },
                        )
                    }
                }
                if (sections.all { it.games.isEmpty() } && !model.loading && err == null) item(key = "none") {
                    Text("No games with a working stream right now.", color = colors.textSecondary, fontSize = 15.sp)
                }
            }
        }

        sections.forEach { s -> gameRow(s, onPlay = onPlay, onMenu = { menuFor = it }) }
    }

    menuFor?.let { g -> FavoriteDialog(g, model, onDismiss = { menuFor = null }) }
}

private fun LazyListScope.chipRow(key: String, title: String?, content: LazyListScope.() -> Unit) {
    item(key = key) {
        Column {
            if (title != null) {
                Text(
                    title, color = MaterialTheme.nuvio.colors.textMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

private fun LazyListScope.gameRow(s: Section, onPlay: (String) -> Unit, onMenu: (SportsApi.Game) -> Unit) {
    item(key = "row:" + s.key) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                if (s.games.isEmpty()) s.title else "${s.title}  ${s.games.size}",
                color = if (s.gold) Gold else MaterialTheme.nuvio.colors.textPrimary, fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp),
            )
            if (s.games.isEmpty()) {
                Text(s.empty.orEmpty(), color = MaterialTheme.nuvio.colors.textMuted, fontSize = 14.sp)
                return@Column
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(s.games, key = { it.id }) { g ->
                    GameCard(g, onClick = { onPlay(g.id) }, onLongClick = { onMenu(g) })
                }
            }
        }
    }
}

internal fun sortGames(games: List<SportsApi.Game>) =
    games.sortedWith(compareBy({ !it.isLive }, { it.status == "final" }, { it.start }))

/** Search results and filtered views: live, then today, then the rest of the week. */
private fun byWhen(games: List<SportsApi.Game>): List<Section> = listOf(
    Section("Live now", sortGames(games.filter { it.isLive })),
    Section("Today", sortGames(games.filter { !it.isLive && SportsTime.isToday(it) })),
    Section("This week", sortGames(games.filter { !SportsTime.isToday(it) })),
).filter { it.games.isNotEmpty() }

private fun homeSections(games: List<SportsApi.Game>, fav: SportsApi.Favorites): List<Section> {
    val today = games.filter { SportsTime.isToday(it) }
    val favSports = fav.items.filter { it.kind == "sports" }.map { it.name }
    val bySport = today.groupBy { it.sport }.entries
        .sortedWith(compareBy({ it.key !in favSports }, { -it.value.size }))
        .map { (sport, gs) -> Section(sport, sortGames(gs)) }
    // Each favorite keeps its row even with nothing this week (at the bottom of the
    // favorites), so adding one always shows.
    val favRows = fav.items.map { f ->
        Section(
            "★ ${f.label}", sortGames(games.filter(SportsFilter(f.kind, f.name, f.label)::matches)),
            key = "fav:${f.kind}:${f.name}", empty = "No games this week", gold = true,
        )
    }.sortedBy { it.games.isEmpty() }
    val live = Section("Live now", sortGames(games.filter { it.isLive }))
    return listOf(live).filter { it.games.isNotEmpty() } + favRows + bySport
}

/** Sports, then leagues, that have games this week, busiest first. */
private fun browseChips(games: List<SportsApi.Game>): List<Pair<SportsFilter, Int>> {
    val sports = games.groupBy { it.sport }.map { (s, g) -> SportsFilter("sports", s, s) to g.size }
    val leagues = games.groupBy { it.league }.map { (code, g) -> SportsFilter("leagues", code, g.first().leagueName) to g.size }
        .filter { (f, _) -> sports.none { it.first.label == f.label } }
    return sports.sortedByDescending { it.second } + leagues.sortedByDescending { it.second }
}
