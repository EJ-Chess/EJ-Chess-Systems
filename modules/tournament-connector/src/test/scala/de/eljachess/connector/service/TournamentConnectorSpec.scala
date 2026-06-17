package de.eljachess.connector.service

import de.eljachess.connector.client.{BotHttpClient, TournamentHttpClient}
import de.eljachess.connector.config.ConnectorConfig
import de.eljachess.connector.dto.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.ConcurrentHashMap

class TournamentConnectorSpec extends AnyFlatSpec with Matchers:

  // ── Helpers ─────────────────────────────────────────────────────────────────

  private def cfg(tournamentId: Option[String] = None, pollSec: Int = 1): ConnectorConfig =
    val c = new ConnectorConfig
    c.serverUrl          = "http://server"
    c.botName            = "TestBot"
    c.botElo             = 1400
    c.botServiceUrl      = "http://bot"
    c.tournamentId       = tournamentId.fold(java.util.Optional.empty[String]())(java.util.Optional.of)
    c.pollIntervalSeconds = pollSec
    c.enabled            = true
    c

  private def connector(
    config: ConnectorConfig,
    tournaments: List[TournamentSummary] = Nil,
    joinResult: Either[String, Unit] = Right(()),
    registerResult: Either[String, RegisterResponse] = Right(RegisterResponse("b1", "tok1"))
  ): TournamentConnector =
    val c = new TournamentConnector
    c.config = config
    c.httpClient = new TournamentHttpClient:
      override def registerBot(serverUrl: String, name: String): Either[String, RegisterResponse] = registerResult
      override def listTournaments(serverUrl: String): Either[String, List[TournamentSummary]] = Right(tournaments)
      override def joinTournament(serverUrl: String, tournamentId: String, token: String): Either[String, Unit] = joinResult
      override def streamTournamentEvents(serverUrl: String, tournamentId: String, token: String, onEvent: TournamentStreamEvent => Unit): Unit = ()
    c.botClient = new BotHttpClient:
      override def getBotMove(botServiceUrl: String, fen: String, color: String, elo: Int): Option[String] = Some("e2e4")
    c

  // ── selectTournament ────────────────────────────────────────────────────────

  "selectTournament" should "pick a specific tournament by configured ID" in:
    val c = connector(cfg(Some("t2")))
    val ts = List(
      TournamentSummary("t1", "created"),
      TournamentSummary("t2", "started"),
      TournamentSummary("t3", "finished")
    )
    c.selectTournament(ts).map(_.id) shouldBe Some("t2")

  it should "return None when configured ID is not in the list" in:
    val c = connector(cfg(Some("t-missing")))
    val ts = List(TournamentSummary("t1", "created"))
    c.selectTournament(ts) shouldBe None

  it should "auto-pick the first 'created' tournament when no ID configured" in:
    val c = connector(cfg(None))
    val ts = List(
      TournamentSummary("t1", "finished"),
      TournamentSummary("t2", "created"),
      TournamentSummary("t3", "created")
    )
    c.selectTournament(ts).map(_.id) shouldBe Some("t2")

  it should "auto-pick a 'started' tournament if no 'created' one exists" in:
    val c = connector(cfg(None))
    val ts = List(
      TournamentSummary("t1", "finished"),
      TournamentSummary("t2", "started")
    )
    c.selectTournament(ts).map(_.id) shouldBe Some("t2")

  it should "return None when list is empty" in:
    val c = connector(cfg(None))
    c.selectTournament(Nil) shouldBe None

  it should "return None when only finished tournaments exist" in:
    val c = connector(cfg(None))
    val ts = List(TournamentSummary("t1", "finished"))
    c.selectTournament(ts) shouldBe None

  // ── tryFindAndJoin ──────────────────────────────────────────────────────────

  "tryFindAndJoin" should "return tournament ID on success" in:
    val ts = List(TournamentSummary("t1", "created"))
    val c = connector(cfg(None), tournaments = ts)
    c.tryFindAndJoin("tok") shouldBe Some("t1")

  it should "return None when list is empty" in:
    val c = connector(cfg(None), tournaments = Nil)
    c.tryFindAndJoin("tok") shouldBe None

  it should "return None when join fails" in:
    val ts = List(TournamentSummary("t1", "created"))
    val c = connector(cfg(None), tournaments = ts, joinResult = Left("already joined"))
    c.tryFindAndJoin("tok") shouldBe None

  it should "return None when tournament list call fails" in:
    val c = new TournamentConnector
    c.config = cfg()
    c.httpClient = new TournamentHttpClient:
      override def listTournaments(serverUrl: String): Either[String, List[TournamentSummary]] = Left("network error")
      override def joinTournament(serverUrl: String, tournamentId: String, token: String): Either[String, Unit] = Right(())
      override def streamTournamentEvents(serverUrl: String, tournamentId: String, token: String, onEvent: TournamentStreamEvent => Unit): Unit = ()
      override def registerBot(serverUrl: String, name: String): Either[String, RegisterResponse] = Right(RegisterResponse("b1","t"))
    c.botClient = new BotHttpClient
    c.tryFindAndJoin("tok") shouldBe None

  // ── registerWithRetry ───────────────────────────────────────────────────────

  "registerWithRetry" should "return Some on the first successful registration" in:
    val c = connector(cfg(), registerResult = Right(RegisterResponse("b42", "jwt-42")))
    c.registerWithRetry(maxAttempts = 3) shouldBe Some(("b42", "jwt-42"))

  it should "return None after all attempts fail" in:
    val c = new TournamentConnector
    c.config = cfg()
    c.httpClient = new TournamentHttpClient:
      override def registerBot(serverUrl: String, name: String): Either[String, RegisterResponse] = Left("server down")
      override def listTournaments(serverUrl: String): Either[String, List[TournamentSummary]] = Right(Nil)
      override def joinTournament(serverUrl: String, tournamentId: String, token: String): Either[String, Unit] = Right(())
      override def streamTournamentEvents(serverUrl: String, tournamentId: String, token: String, onEvent: TournamentStreamEvent => Unit): Unit = ()
    c.botClient = new BotHttpClient
    c.registerWithRetry(maxAttempts = 1) shouldBe None

  // ── handleTournamentEvent ───────────────────────────────────────────────────

  "handleTournamentEvent" should "spawn a game handler for a new gameStart event" in:
    val streamedGames = collection.mutable.ListBuffer[String]()
    val tc = new TournamentHttpClient:
      override def streamGameEvents(serverUrl: String, tournamentId: String, gameId: String, token: String, onEvent: GameStreamEvent => Unit): Unit =
        streamedGames.synchronized { streamedGames += gameId }
      override def registerBot(serverUrl: String, name: String): Either[String, RegisterResponse] = Right(RegisterResponse("b1","t"))
      override def listTournaments(serverUrl: String): Either[String, List[TournamentSummary]] = Right(Nil)
      override def joinTournament(serverUrl: String, tournamentId: String, token: String): Either[String, Unit] = Right(())
      override def streamTournamentEvents(serverUrl: String, tournamentId: String, token: String, onEvent: TournamentStreamEvent => Unit): Unit = ()
      override def submitMove(serverUrl: String, tournamentId: String, gameId: String, uci: String, token: String): Either[String, Unit] = Right(())

    val c = new TournamentConnector
    c.config = cfg()
    c.httpClient = tc
    c.botClient = new BotHttpClient:
      override def getBotMove(botServiceUrl: String, fen: String, color: String, elo: Int): Option[String] = Some("e2e4")

    val event = TournamentStreamEvent(gameStart = Some(GameStartInfo(1, "g-99", "white")))
    c.handleTournamentEvent(event, "t1", "token")

    // Give the virtual thread a moment to start
    Thread.sleep(200)
    streamedGames should contain("g-99")

  it should "not spawn a duplicate handler for the same gameId" in:
    var callCount = 0
    val tc = new TournamentHttpClient:
      override def streamGameEvents(serverUrl: String, tournamentId: String, gameId: String, token: String, onEvent: GameStreamEvent => Unit): Unit =
        callCount += 1
      override def registerBot(serverUrl: String, name: String): Either[String, RegisterResponse] = Right(RegisterResponse("b1","t"))
      override def listTournaments(serverUrl: String): Either[String, List[TournamentSummary]] = Right(Nil)
      override def joinTournament(serverUrl: String, tournamentId: String, token: String): Either[String, Unit] = Right(())
      override def streamTournamentEvents(serverUrl: String, tournamentId: String, token: String, onEvent: TournamentStreamEvent => Unit): Unit = ()
      override def submitMove(serverUrl: String, tournamentId: String, gameId: String, uci: String, token: String): Either[String, Unit] = Right(())

    val c = new TournamentConnector
    c.config = cfg()
    c.httpClient = tc
    c.botClient = new BotHttpClient:
      override def getBotMove(botServiceUrl: String, fen: String, color: String, elo: Int): Option[String] = None

    val event = TournamentStreamEvent(gameStart = Some(GameStartInfo(1, "g-dup", "white")))
    c.handleTournamentEvent(event, "t1", "tok")
    c.handleTournamentEvent(event, "t1", "tok")   // duplicate

    Thread.sleep(200)
    callCount shouldBe 1

  it should "handle a tournamentFinished event without error" in:
    val c = connector(cfg())
    val event = TournamentStreamEvent(tournamentFinished = Some(WinnerInfo(Some("b1"), Some("Alpha"))))
    noException should be thrownBy c.handleTournamentEvent(event, "t1", "tok")

  it should "handle an empty event without error" in:
    val c = connector(cfg())
    noException should be thrownBy c.handleTournamentEvent(TournamentStreamEvent(), "t1", "tok")
