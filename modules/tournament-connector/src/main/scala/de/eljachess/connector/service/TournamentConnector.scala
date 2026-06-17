package de.eljachess.connector.service

import de.eljachess.connector.client.{BotHttpClient, TournamentHttpClient}
import de.eljachess.connector.config.ConnectorConfig
import de.eljachess.connector.dto.{TournamentStreamEvent, TournamentSummary}
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import scala.compiletime.uninitialized
import java.util.concurrent.ConcurrentHashMap

/** Startup bean that registers our bot with the external tournament server
 *  and keeps it playing in tournaments.
 *
 *  On start-up it:
 *  1. Registers the bot (POST /api/auth/register) to obtain a JWT.
 *  2. Finds the target tournament — either the configured ID or the first
 *     "created" one available; retries every [pollIntervalSeconds] until found.
 *  3. Joins the tournament.
 *  4. Opens the tournament event stream and dispatches a virtual thread per
 *     game whenever a "gameStart" event arrives.
 *
 *  Set `connector.enabled=false` (or `%test.connector.enabled=false`) to
 *  prevent the connector from dialling out (e.g. in tests). */
@ApplicationScoped
class TournamentConnector:
  @Inject var config: ConnectorConfig = uninitialized
  @Inject var httpClient: TournamentHttpClient = uninitialized
  @Inject var botClient: BotHttpClient = uninitialized

  /** IDs of games we have already started handling (prevents duplicate handlers
   *  when the tournament stream broadcasts two gameStart events per pairing). */
  private val activeGames = ConcurrentHashMap.newKeySet[String]()

  def onStart(@Observes ev: StartupEvent): Unit =
    if config.enabled then
      Thread.ofVirtual().name("tournament-connector").start(() => run())

  // ── Core loop ──────────────────────────────────────────────────────────────

  private[service] def run(): Unit =
    val (botId, token) = registerWithRetry() match
      case Some(pair) => pair
      case None =>
        println("[TournamentConnector] Registration failed permanently — giving up")
        return

    val tournamentId = findAndJoinTournament(token) match
      case Some(id) => id
      case None =>
        println("[TournamentConnector] Could not find a tournament to join — giving up")
        return

    println(s"[TournamentConnector] Streaming events for tournament $tournamentId")
    httpClient.streamTournamentEvents(
      config.serverUrl, tournamentId, token,
      event => handleTournamentEvent(event, tournamentId, token)
    )
    println("[TournamentConnector] Tournament stream ended")

  // ── Registration ───────────────────────────────────────────────────────────

  private[service] def registerWithRetry(maxAttempts: Int = 5): Option[(String, String)] =
    (1 to maxAttempts).iterator.flatMap { attempt =>
      httpClient.registerBot(config.serverUrl, config.botName) match
        case Right(r) =>
          println(s"[TournamentConnector] Registered as ${config.botName} (id=${r.id})")
          Some((r.id, r.token))
        case Left(err) =>
          println(s"[TournamentConnector] Registration attempt $attempt failed: $err")
          if attempt < maxAttempts then Thread.sleep(5000L)
          None
    }.nextOption()

  // ── Tournament selection ───────────────────────────────────────────────────

  /** Polls until a suitable tournament is found, then joins it. */
  private[service] def findAndJoinTournament(token: String): Option[String] =
    var found: Option[String] = None
    while found.isEmpty do
      found = tryFindAndJoin(token)
      if found.isEmpty then
        println(s"[TournamentConnector] No tournament available, retrying in ${config.pollIntervalSeconds}s…")
        Thread.sleep(config.pollIntervalSeconds * 1000L)
    found

  private[service] def tryFindAndJoin(token: String): Option[String] =
    httpClient.listTournaments(config.serverUrl) match
      case Left(err) =>
        println(s"[TournamentConnector] Could not list tournaments: $err")
        None
      case Right(tournaments) =>
        selectTournament(tournaments) match
          case None =>
            println("[TournamentConnector] No joinable tournament found in list")
            None
          case Some(t) =>
            httpClient.joinTournament(config.serverUrl, t.id, token) match
              case Right(_) =>
                println(s"[TournamentConnector] Joined tournament ${t.id}")
                Some(t.id)
              case Left(err) =>
                println(s"[TournamentConnector] Could not join tournament ${t.id}: $err")
                None

  /** Picks the target tournament from the available list.
   *  If [config.tournamentId] is set, finds that specific tournament.
   *  Otherwise, returns the first tournament with status "created" or "started". */
  private[service] def selectTournament(tournaments: List[TournamentSummary]): Option[TournamentSummary] =
    val configuredId = Option(config.tournamentId).flatMap(o => if o.isPresent then Some(o.get()) else None)
    configuredId match
      case Some(id) => tournaments.find(_.id == id)
      case None     => tournaments.find(t => t.status == "created" || t.status == "started")

  // ── Event dispatch ─────────────────────────────────────────────────────────

  private[service] def handleTournamentEvent(
    event: TournamentStreamEvent,
    tournamentId: String,
    token: String
  ): Unit =
    event.tournamentFinished.foreach { w =>
      println(s"[TournamentConnector] Tournament $tournamentId finished. Winner: ${w.name.getOrElse("unknown")}")
    }
    event.gameStart.foreach { gs =>
      if activeGames.add(gs.gameId) then
        // First gameStart for this game — spawn a handler
        val handler = GameHandler(
          tournamentId = tournamentId,
          gameId       = gs.gameId,
          myColor      = gs.color,
          token        = token,
          serverUrl    = config.serverUrl,
          botServiceUrl = config.botServiceUrl,
          elo          = config.botElo,
          httpClient   = httpClient,
          botClient    = botClient
        )
        Thread.ofVirtual().name(s"game-${gs.gameId}").start(() => handler.run())
      // else: duplicate event for the same game — ignore
    }
