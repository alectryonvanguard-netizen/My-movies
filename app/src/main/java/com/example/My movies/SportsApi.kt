package com.mymovie.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** TheSportsDB (free public key works for schedules/results/tables; premium key unlocks live scores etc.). */
interface SportsDbApi {
    @GET("api/v1/json/{key}/all_leagues.php") suspend fun allLeagues(@Path("key") key: String): TsdbLeagues
    @GET("api/v1/json/{key}/eventsday.php") suspend fun eventsDay(@Path("key") key: String, @Query("d") date: String, @Query("s") sport: String): TsdbEvents
    @GET("api/v1/json/{key}/eventsnextleague.php") suspend fun nextEvents(@Path("key") key: String, @Query("id") id: String): TsdbEvents
    @GET("api/v1/json/{key}/eventspastleague.php") suspend fun pastEvents(@Path("key") key: String, @Query("id") id: String): TsdbEvents
    @GET("api/v1/json/{key}/lookuptable.php") suspend fun table(@Path("key") key: String, @Query("l") league: String, @Query("s") season: String): TsdbTable
    @GET("api/v1/json/{key}/lookuptimeline.php") suspend fun timeline(@Path("key") key: String, @Query("id") id: String): TsdbTimeline
    @GET("api/v1/json/{key}/lookupeventstats.php") suspend fun stats(@Path("key") key: String, @Query("id") id: String): TsdbStats
    @GET("api/v1/json/{key}/searchteams.php") suspend fun teams(@Path("key") key: String, @Query("t") q: String): TsdbTeams
    @GET("api/v1/json/{key}/searchplayers.php") suspend fun players(@Path("key") key: String, @Query("p") q: String): TsdbPlayers
    @GET("api/v1/json/{key}/searchevents.php") suspend fun searchEvents(@Path("key") key: String, @Query("e") q: String): TsdbEvents
    @GET("api/v2/json/livescore/{sport}") suspend fun live(@Path("sport") sport: String): TsdbLive
}

@Serializable data class TsdbLeagues(val leagues: List<TsdbLeague>? = null)
@Serializable data class TsdbLeague(val idLeague: String, val strLeague: String? = null, val strSport: String? = null)
@Serializable data class TsdbEvents(val events: List<TsdbEvent>? = null, val event: List<TsdbEvent>? = null)
@Serializable data class TsdbLive(val livescore: List<TsdbEvent>? = null)
@Serializable
data class TsdbEvent(
    val idEvent: String? = null, val strEvent: String? = null, val strSport: String? = null,
    val idLeague: String? = null, val strLeague: String? = null,
    val strHomeTeam: String? = null, val strAwayTeam: String? = null,
    val intHomeScore: String? = null, val intAwayScore: String? = null,
    val strTimestamp: String? = null, val dateEvent: String? = null, val strTime: String? = null,
    val strStatus: String? = null, val strProgress: String? = null, val strVenue: String? = null,
    val strHomeTeamBadge: String? = null, val strAwayTeamBadge: String? = null,
    val strThumb: String? = null, val strVideo: String? = null
)
@Serializable data class TsdbTable(val table: List<TsdbRow>? = null)
@Serializable
data class TsdbRow(
    val strTeam: String? = null, val strBadge: String? = null, val intRank: String? = null,
    val intPlayed: String? = null, val intWin: String? = null, val intDraw: String? = null, val intLoss: String? = null,
    val intGoalsFor: String? = null, val intGoalsAgainst: String? = null, val intGoalDifference: String? = null,
    val intPoints: String? = null, val strForm: String? = null
)
@Serializable data class TsdbTimeline(val timeline: List<TsdbTimelineEntry>? = null)
@Serializable data class TsdbTimelineEntry(val intTime: String? = null, val strTimeline: String? = null, val strTimelineDetail: String? = null, val strPlayer: String? = null, val strTeam: String? = null)
@Serializable data class TsdbStats(val eventstats: List<TsdbStat>? = null)
@Serializable data class TsdbStat(val strStat: String? = null, val intHome: String? = null, val intAway: String? = null)
@Serializable data class TsdbTeams(val teams: List<TsdbTeam>? = null)
@Serializable data class TsdbTeam(val strTeam: String? = null, val strSport: String? = null, val strLeague: String? = null, val strStadium: String? = null, val strBadge: String? = null, val strCountry: String? = null, val strDescriptionEN: String? = null)
@Serializable data class TsdbPlayers(val player: List<TsdbPlayer>? = null)
@Serializable data class TsdbPlayer(val strPlayer: String? = null, val strTeam: String? = null, val strSport: String? = null, val strPosition: String? = null, val strNationality: String? = null, val strThumb: String? = null, val strCutout: String? = null)

/** football-data.org (requires the user's own free API key). */
interface FootballDataApi {
    @GET("v4/competitions") suspend fun competitions(): FdCompetitions
    @GET("v4/competitions/{code}/standings") suspend fun standings(@Path("code") code: String): FdStandings
    @GET("v4/competitions/{code}/scorers") suspend fun scorers(@Path("code") code: String, @Query("limit") limit: Int): FdScorers
    @GET("v4/competitions/{code}/matches") suspend fun matches(@Path("code") code: String, @Query("status") status: String): FdMatches
    @GET("v4/matches") suspend fun matchesBetween(@Query("dateFrom") from: String, @Query("dateTo") to: String): FdMatches
}

@Serializable data class FdCompetitions(val competitions: List<FdCompetition> = emptyList())
@Serializable data class FdCompetition(val name: String? = null, val code: String? = null, val emblem: String? = null)
@Serializable data class FdTeam(val name: String? = null, val shortName: String? = null, val crest: String? = null)
@Serializable data class FdStandings(val standings: List<FdGroup> = emptyList())
@Serializable data class FdGroup(val type: String? = null, val table: List<FdRow> = emptyList())
@Serializable
data class FdRow(
    val position: Int = 0, val team: FdTeam = FdTeam(), val playedGames: Int = 0, val form: String? = null,
    val won: Int = 0, val draw: Int = 0, val lost: Int = 0, val points: Int = 0,
    val goalsFor: Int = 0, val goalsAgainst: Int = 0, val goalDifference: Int = 0
)
@Serializable data class FdScorers(val scorers: List<FdScorer> = emptyList())
@Serializable data class FdPlayer(val name: String? = null)
@Serializable data class FdScorer(val player: FdPlayer = FdPlayer(), val team: FdTeam = FdTeam(), val goals: Int? = null, val assists: Int? = null, val playedMatches: Int? = null)
@Serializable data class FdMatches(val matches: List<FdMatch> = emptyList())
@Serializable data class FdGoals(val home: Int? = null, val away: Int? = null)
@Serializable data class FdScore(val fullTime: FdGoals = FdGoals())
@Serializable
data class FdMatch(
    val id: Int = 0, val utcDate: String? = null, val status: String? = null,
    val competition: FdCompetition? = null, val homeTeam: FdTeam = FdTeam(), val awayTeam: FdTeam = FdTeam(),
    val score: FdScore = FdScore()
)
