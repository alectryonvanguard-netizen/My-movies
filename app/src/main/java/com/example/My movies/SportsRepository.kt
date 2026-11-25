
package com.mymovie.app.data

import com.mymovie.app.data.remote.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class EventState { LIVE, UPCOMING, FINISHED, POSTPONED, UNKNOWN }

data class SportOption(
    val label: String,
    val tsdb: List<String>,
    val keywords: List<String> = emptyList()
)

val SPORT_OPTIONS = listOf(
    SportOption("Football", listOf("Soccer")),
    SportOption("Basketball", listOf("Basketball")),
    SportOption("Tennis", listOf("Tennis")),
    SportOption("Golf", listOf("Golf")),
    SportOption("Wrestling", listOf("Wrestling", "Fighting"), listOf("wwe", "aew", "wrestl", "njpw")),
    SportOption("Rugby", listOf("Rugby")),
    SportOption("Cricket", listOf("Cricket")),
    SportOption("Motorsport", listOf("Motorsport")),
    SportOption("American Football", listOf("American Football")),
    SportOption("Baseball", listOf("Baseball")),
    SportOption("Ice Hockey", listOf("Ice Hockey")),
    SportOption("Combat", listOf("Fighting"))
)

data class SportEvent(
    val id: String,
    val provider: String,
    val sport: String,
    val league: String,
    val leagueId: String?,
    val title: String,
    val home: String?,
    val away: String?,
    val homeBadge: String?,
    val awayBadge: String?,
    val homeScore: Int?,
    val awayScore: Int?,
    val state: EventState,
    val statusText: String,
    val startMillis: Long?,
    val venue: String?,
    val videoUrl: String?,
    val thumb: String?
) {
    val key: String get() = "event:$provider:$id"
}

data class SportLeague(
    val id: String,
    val name: String,
    val sport: String,
    val provider: String,
    val badge: String? = null
)

data class StandingRow(
    val rank: Int,
    val team: String,
    val badge: String?,
    val played: Int,
    val won: Int,
    val draw: Int,
    val lost: Int,
    val gf: Int,
    val ga: Int,
    val gd: Int,
    val points: Int,
    val form: String?
)

data class ScorerRow(
    val rank: Int,
    val player: String,
    val team: String,
    val goals: Int,
    val assists: Int?,
    val played: Int?
)

data class TimelineEntry(val minute: String, val text: String, val team: String)
data class StatEntry(val name: String, val home: String, val away: String)
data class EventExtras(val timeline: List<TimelineEntry>, val stats: List<StatEntry>, val note: String?)

data class TeamResult(
    val name: String,
    val sport: String,
    val league: String,
    val stadium: String,
    val badge: String?,
    val country: String,
    val description: String
)

data class PlayerResult(
    val name: String,
    val team: String,
    val sport: String,
    val position: String,
    val nationality: String,
    val photo: String?
)

private val POPULAR = listOf(
    "English Premier League", "Spanish La Liga", "German Bundesliga",
    "Italian Serie A", "French Ligue 1", "UEFA Champions League",
    "NBA", "NFL", "MLB", "NHL", "Indian Premier League",
    "Formula 1", "UFC", "WWE", "ATP World Tour", "PGA Tour"
)

private fun parseStart(ts: String?, date: String?, time: String?): Long? {
    val s = ts?.takeIf { it.isNotBlank() }
        ?.replace(' ', 'T')
        ?.substringBefore('+')
        ?.substringBefore('Z')
        ?.take(19)
        ?: if (!date.isNullOrBlank() && !time.isNullOrBlank()) {
            "${date}T${time.take(8)}"
        } else {
            date?.takeIf { it.isNotBlank() }?.let { "${it}T00:00:00" }
        }

    return runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(s!!)?.time
    }.getOrNull()
}

private fun dayString(offset: Int): String {
    val c = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, offset)
    }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
}

private fun mapState(
    status: String?,
    progress: String?,
    start: Long?,
    hasScore: Boolean
): Pair<EventState, String> {
    val s = status?.trim().orEmpty()
    val u = s.uppercase()
    val now = System.currentTimeMillis()

    val notStarted = u in setOf(
        "", "NS", "NOT STARTED", "TBD", "SCHEDULED", "TIMED"
    )

    return when {
        u.contains("POSTPONED") || u.contains("CANCEL") ||
            u.contains("SUSPEND") || u.contains("ABANDON") ->
            EventState.POSTPONED to s

        u in setOf(
            "FT", "AET", "PEN", "AOT", "FINISHED",
            "MATCH FINISHED", "FULL TIME", "AFTER EXTRA TIME"
        ) || u.contains("FINISHED") || u.startsWith("FINAL") ->
            EventState.FINISHED to "Full time"

        u in setOf(
            "1H", "2H", "HT", "ET", "BT", "P", "LIVE",
            "IN PROGRESS", "INT", "Q1", "Q2", "Q3", "Q4",
            "OT", "IN_PLAY", "PAUSED", "HALFTIME", "HALF TIME"
        ) || u.startsWith("LIVE") ||
            (!progress.isNullOrBlank() && !notStarted) ->
            EventState.LIVE to (
                progress?.takeIf { it.isNotBlank() }?.let { "$it'" }
                    ?: s.ifBlank { "Live" }
                )

        notStarted && (start == null || start > now - 15 * 60_000L) ->
            EventState.UPCOMING to "Scheduled"

        hasScore && start != null && start < now - 3 * 3600_000L ->
            EventState.FINISHED to "Full time"

        start != null && start < now - 3 * 3600_000L ->
            EventState.UNKNOWN to "Result not available"

        else ->
            EventState.UNKNOWN to "Started · live status not reported by this data plan"
    }
}

class SportsRepository(
    private val tsdb: SportsDbApi,
    private val fd: FootballDataApi,
    private val prefs: Prefs
) {
    private val key get() = prefs.sportsDbKey
    val hasFd get() = prefs.footballDataKey.isNotBlank()
    val premiumTsdb get() = key != DEFAULT_TSDB_KEY

    private var leaguesCache: List<TsdbLeague>? = null

    private suspend fun allLeagues(): List<TsdbLeague> =
        leaguesCache ?: tsdb.allLeagues(key).leagues.orEmpty()
            .also { leaguesCache = it }

    private fun TsdbEvent.toEvent(): SportEvent? {
        val id = idEvent ?: return null
        val start = parseStart(strTimestamp, dateEvent, strTime)
        val hs = intHomeScore?.toIntOrNull()
        val aw = intAwayScore?.toIntOrNull()

        val (st, txt) = mapState(
            strStatus,
            strProgress,
            start,
            hs != null && aw != null
        )

        val title =
            if (!strHomeTeam.isNullOrBlank() && !strAwayTeam.isNullOrBlank()) {
                "$strHomeTeam vs $strAwayTeam"
            } else {
                strEvent.orEmpty()
            }

        return SportEvent(
            id, "tsdb", strSport.orEmpty(), strLeague.orEmpty(),
            idLeague, title, strHomeTeam, strAwayTeam,
            strHomeTeamBadge, strAwayTeamBadge, hs, aw,
            st, txt, start, strVenue,
            strVideo?.takeIf { it.startsWith("http") }, strThumb
        )
    }

    private fun FdMatch.toEvent(): SportEvent {
        val st = status.orEmpty()

        val (state, text) = when (st) {
            "IN_PLAY" -> EventState.LIVE to "Live"
            "PAUSED" -> EventState.LIVE to "Half-time"
            "FINISHED", "AWARDED" -> EventState.FINISHED to "Full time"
            "POSTPONED", "CANCELLED", "SUSPENDED" ->
                EventState.POSTPONED to st.lowercase().replaceFirstChar { it.uppercase() }
            "SCHEDULED", "TIMED" -> EventState.UPCOMING to "Scheduled"
            else -> EventState.UNKNOWN to st
        }

        val h = homeTeam.shortName ?: homeTeam.name
        val a = awayTeam.shortName ?: awayTeam.name

        return SportEvent(
            id.toString(), "fd", "Soccer",
            competition?.name.orEmpty(), competition?.code,
            "$h vs $a", h, a, homeTeam.crest, awayTeam.crest,
            score.fullTime.home, score.fullTime.away,
            state, text, parseStart(utcDate, null, null),
            null, null, null
        )
    }

    suspend fun leagues(sport: SportOption): List<SportLeague> {
        val out = ArrayList<SportLeague>()

        if (sport.label == "Football" && hasFd) {
            runCatching { fd.competitions().competitions }
                .getOrNull()?.forEach {
                    if (it.code != null && it.name != null) {
                        out += SportLeague(
                            it.code, it.name, sport.label, "fd", it.emblem
                        )
                    }
                }
        }

        val ts = allLeagues().filter { league ->
            league.strSport?.let { s ->
                sport.tsdb.any { it.equals(s, true) }
            } == true ||
                sport.keywords.any {
                    (league.strLeague ?: "").contains(it, true)
                }
        }.map {
            SportLeague(it.idLeague, it.strLeague.orEmpty(), sport.label, "tsdb")
        }.sortedWith(
            compareBy(
                { league ->
                    POPULAR.indexOfFirst { it.equals(league.name, true) }
                        .let { if (it < 0) 1000 else it }
                },
                { it.name }
            )
        )

        return out + ts
    }

    suspend fun today(sport: SportOption): List<SportEvent> = coroutineScope {
        val tsEvents = (-1..1).flatMap { offset ->
            sport.tsdb.map { sportName ->
                offset to sportName
            }
        }.map { (offset, sportName) ->
            async {
                runCatching {
                    val response = tsdb.eventsDay(
                        key,
                        dayString(offset),
                        sportName
                    )
                    response.events ?: response.event ?: emptyList()
                }.getOrDefault(emptyList())
            }
        }

        val fdToday = if (sport.label == "Football" && hasFd) {
            async {
                runCatching {
                    fd.matchesBetween(
                        dayString(-1),
                        dayString(1)
                    ).matches.map { it.toEvent() }
                }.getOrDefault(emptyList())
            }
        } else null

        val live = if (premiumTsdb && sport.tsdb.isNotEmpty()) {
            async {
                runCatching {
                    tsdb.live(sport.tsdb.first()).livescore.orEmpty()
                }.getOrDefault(emptyList())
            }
        } else null

        var events: List<SportEvent> =
            tsEvents.flatMap { it.await() }
                .filter { event ->
                    sport.keywords.isEmpty() ||
                        sport.label != "Wrestling" ||
                        sport.keywords.any { keyword ->
                            (event.strLeague ?: "").contains(keyword, true)
                        }
                }
                .mapNotNull { event -> event.toEvent() } +
                (fdToday?.await() ?: emptyList())

        val liveEvents = live?.await().orEmpty()
            .mapNotNull { event -> event.toEvent() }

        liveEvents.forEach { event ->
            events = events.filterNot {
                it.id == event.id && it.provider == event.provider
            } + event.copy(
                state = if (event.state == EventState.UNKNOWN) {
                    EventState.LIVE
                } else {
                    event.state
                }
            )
        }

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        events.filter { event ->
            event.state == EventState.LIVE ||
                (event.startMillis ?: 0L) in
                startOfToday until startOfToday + 86_400_000L
        }
            .distinctBy { event -> event.provider + event.id }
            .sortedWith(
                compareBy<SportEvent>(
                    { it.state != EventState.LIVE },
                    { it.startMillis ?: Long.MAX_VALUE }
                )
            )
    }

    suspend fun fixtures(l: SportLeague): List<SportEvent> =
        if (l.provider == "fd") {
            fd.matches(l.id, "SCHEDULED,TIMED").matches
                .map { it.toEvent() }
                .sortedBy { it.startMillis }
                .take(40)
        } else {
            (tsdb.nextEvents(key, l.id).let {
                it.events ?: it.event
            }.orEmpty())
                .mapNotNull { it.toEvent() }
                .sortedBy { it.startMillis }
        }

    suspend fun results(l: SportLeague): List<SportEvent> =
        if (l.provider == "fd") {
            fd.matches(l.id, "FINISHED").matches
                .map { it.toEvent() }
                .sortedByDescending { it.startMillis }
                .take(40)
        } else {
            (tsdb.pastEvents(key, l.id).let {
                it.events ?: it.event
            }.orEmpty())
                .mapNotNull { it.toEvent() }
                .sortedByDescending { it.startMillis }
        }

    suspend fun standings(l: SportLeague): List<StandingRow> {
        if (l.provider == "fd") {
            val response = fd.standings(l.id)
            val group = response.standings.firstOrNull {
                it.type == "TOTAL"
            } ?: response.standings.firstOrNull()

            return group?.table.orEmpty().map {
                StandingRow(
                    it.position,
                    it.team.shortName ?: it.team.name.orEmpty(),
                    it.team.crest,
                    it.playedGames,
                    it.won,
                    it.draw,
                    it.lost,
                    it.goalsFor,
                    it.goalsAgainst,
                    it.goalDifference,
                    it.points,
                    it.form?.replace(",", "")
                )
            }
        }

        val c = Calendar.getInstance()
        val y = c.get(Calendar.YEAR)
        val split = if (c.get(Calendar.MONTH) >= 6) {
            "$y-${y + 1}"
        } else {
            "${y - 1}-$y"
        }

        for (season in listOf(split, "$y", "${y - 1}-$y").distinct()) {
            val rows = tsdb.table(key, l.id, season).table

            if (!rows.isNullOrEmpty()) {
                return rows.mapIndexed { i, r ->
                    StandingRow(
                        r.intRank?.toIntOrNull() ?: (i + 1),
                        r.strTeam.orEmpty(),
                        r.strBadge,
                        r.intPlayed?.toIntOrNull() ?: 0,
                        r.intWin?.toIntOrNull() ?: 0,
                        r.intDraw?.toIntOrNull() ?: 0,
                        r.intLoss?.toIntOrNull() ?: 0,
                        r.intGoalsFor?.toIntOrNull() ?: 0,
                        r.intGoalsAgainst?.toIntOrNull() ?: 0,
                        r.intGoalDifference?.toIntOrNull() ?: 0,
                        r.intPoints?.toIntOrNull() ?: 0,
                        r.strForm
                    )
                }
            }
        }

        return emptyList()
    }

    suspend fun scorers(l: SportLeague): List<ScorerRow> =
        if (l.provider != "fd") {
            emptyList()
        } else {
            fd.scorers(l.id, 20).scorers.mapIndexed { i, s ->
                ScorerRow(
                    i + 1,
                    s.player.name.orEmpty(),
                    s.team.shortName ?: s.team.name.orEmpty(),
                    s.goals ?: 0,
                    s.assists,
                    s.playedMatches
                )
            }
        }

    suspend fun extras(e: SportEvent): EventExtras {
        if (e.provider != "tsdb") {
            return EventExtras(
                emptyList(),
                emptyList(),
                "Timeline and statistics are not included in the football-data.org free plan."
            )
        }

        val tl = runCatching {
            tsdb.timeline(key, e.id).timeline.orEmpty()
        }.getOrDefault(emptyList()).map {
            TimelineEntry(
                it.intTime.orEmpty(),
                listOfNotNull(
                    it.strTimeline,
                    it.strPlayer,
                    it.strTimelineDetail
                ).joinToString(" · "),
                it.strTeam.orEmpty()
            )
        }

        val st = runCatching {
            tsdb.stats(key, e.id).eventstats.orEmpty()
        }.getOrDefault(emptyList()).map {
            StatEntry(
                it.strStat.orEmpty(),
                it.intHome.orEmpty(),
                it.intAway.orEmpty()
            )
        }

        return EventExtras(
            tl,
            st,
            if (tl.isEmpty() && st.isEmpty()) {
                "No timeline or statistics are available for this event on the current data plan."
            } else null
        )
    }

    suspend fun searchLeagues(q: String): List<SportLeague> =
        allLeagues()
            .filter { (it.strLeague ?: "").contains(q, true) }
            .take(10)
            .map { league ->
                SportLeague(
                    league.idLeague,
                    league.strLeague.orEmpty(),
                    SPORT_OPTIONS.firstOrNull { option ->
                        option.tsdb.any {
                            it.equals(league.strSport, true)
                        }
                    }?.label ?: league.strSport.orEmpty(),
                    "tsdb"
                )
            }

    suspend fun searchTeams(q: String): List<TeamResult> =
        tsdb.teams(key, q).teams.orEmpty()
            .take(8)
            .map {
                TeamResult(
                    it.strTeam.orEmpty(),
                    it.strSport.orEmpty(),
                    it.strLeague.orEmpty(),
                    it.strStadium.orEmpty(),
                    it.strBadge,
                    it.strCountry.orEmpty(),
                    it.strDescriptionEN.orEmpty()
                )
            }

    suspend fun searchPlayers(q: String): List<PlayerResult> =
        tsdb.players(key, q).player.orEmpty()
            .take(8)
            .map {
                PlayerResult(
                    it.strPlayer.orEmpty(),
                    it.strTeam.orEmpty(),
                    it.strSport.orEmpty(),
                    it.strPosition.orEmpty(),
                    it.strNationality.orEmpty(),
                    it.strCutout ?: it.strThumb
                )
            }

    suspend fun searchEvents(q: String): List<SportEvent> =
        tsdb.searchEvents(key, q)
            .let { it.event ?: it.events }
            .orEmpty()
            .mapNotNull { it.toEvent() }
            .take(8)
}
