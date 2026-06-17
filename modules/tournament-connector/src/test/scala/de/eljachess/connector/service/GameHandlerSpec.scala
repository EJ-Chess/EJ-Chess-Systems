package de.eljachess.connector.service

import de.eljachess.connector.client.{BotHttpClient, TournamentHttpClient}
import de.eljachess.connector.dto.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GameHandlerSpec extends AnyFlatSpec with Matchers:

  // ── Test doubles ────────────────────────────────────────────────────────────

  /** Records every call to submitMove. */
  class RecordingTournamentClient extends TournamentHttpClient:
    val submittedMoves = collection.mutable.ListBuffer[(String, String)]()
    override def submitMove(serverUrl: String, tournamentId: String, gameId: String, uci: String, token: String): Either[String, Unit] =
      submittedMoves += ((gameId, uci))
      Right(())
    override def streamGameEvents(serverUrl: String, tournamentId: String, gameId: String, token: String, onEvent: GameStreamEvent => Unit): Unit = ()

  /** Always returns the configured move. */
  class StubBotClient(move: Option[String]) extends BotHttpClient:
    override def getBotMove(botServiceUrl: String, fen: String, color: String, elo: Int): Option[String] = move

  /** Tracks submitted moves and simulates a bot failure. */
  class FailingBotClient extends BotHttpClient:
    override def getBotMove(botServiceUrl: String, fen: String, color: String, elo: Int): Option[String] = None

  /** Returns an error when submitting a move. */
  class FailingTournamentClient extends TournamentHttpClient:
    override def submitMove(serverUrl: String, tournamentId: String, gameId: String, uci: String, token: String): Either[String, Unit] =
      Left("server error")
    override def streamGameEvents(serverUrl: String, tournamentId: String, gameId: String, token: String, onEvent: GameStreamEvent => Unit): Unit = ()

  private def handler(
    myColor: String = "white",
    botMove: Option[String] = Some("e2e4"),
    tournamentClient: TournamentHttpClient = null,
    botClient: BotHttpClient = null
  ): GameHandler =
    val tc = if tournamentClient != null then tournamentClient else new RecordingTournamentClient
    val bc = if botClient != null then botClient else new StubBotClient(botMove)
    GameHandler("t1", "g1", myColor, "token", "http://server", "http://bot", 1400, tc, bc)

  // ── isMyTurn ────────────────────────────────────────────────────────────────

  "isMyTurn" should "return true when turn matches myColor" in:
    handler("white").isMyTurn("white") shouldBe true

  it should "return false when turn differs" in:
    handler("white").isMyTurn("black") shouldBe false

  it should "work for black" in:
    handler("black").isMyTurn("black") shouldBe true
    handler("black").isMyTurn("white") shouldBe false

  // ── isOngoing ───────────────────────────────────────────────────────────────

  "isOngoing" should "return true for 'ongoing'" in:
    handler().isOngoing("ongoing") shouldBe true

  it should "return true for 'pending'" in:
    handler().isOngoing("pending") shouldBe true

  it should "return false for terminal statuses" in:
    handler().isOngoing("checkmate") shouldBe false
    handler().isOngoing("stalemate") shouldBe false
    handler().isOngoing("draw") shouldBe false
    handler().isOngoing("resigned") shouldBe false
    handler().isOngoing("timeout") shouldBe false

  // ── handleEvent — gameState ─────────────────────────────────────────────────

  "handleEvent" should "submit a move when gameState is our turn" in:
    val tc = new RecordingTournamentClient
    val h = handler("white", Some("e2e4"), tournamentClient = tc)
    val state = GameStateInfo("fen1", turn = "white", status = "ongoing")
    h.handleEvent(GameStreamEvent(gameState = Some(state)))
    tc.submittedMoves.toList shouldBe List(("g1", "e2e4"))

  it should "not submit when gameState is opponent's turn" in:
    val tc = new RecordingTournamentClient
    val h = handler("white", Some("e2e4"), tournamentClient = tc)
    val state = GameStateInfo("fen1", turn = "black", status = "ongoing")
    h.handleEvent(GameStreamEvent(gameState = Some(state)))
    tc.submittedMoves shouldBe empty

  it should "not submit when game is not ongoing" in:
    val tc = new RecordingTournamentClient
    val h = handler("white", Some("e2e4"), tournamentClient = tc)
    val state = GameStateInfo("fen1", turn = "white", status = "checkmate")
    h.handleEvent(GameStreamEvent(gameState = Some(state)))
    tc.submittedMoves shouldBe empty

  // ── handleEvent — move ──────────────────────────────────────────────────────

  it should "submit a move when an opponent's move event arrives and it is our turn" in:
    val tc = new RecordingTournamentClient
    val h = handler("white", Some("d2d4"), tournamentClient = tc)
    val move = MoveInfo("e7e5", "fen2", turn = "white")
    h.handleEvent(GameStreamEvent(move = Some(move)))
    tc.submittedMoves.toList shouldBe List(("g1", "d2d4"))

  it should "not submit when a move event leaves it opponent's turn" in:
    val tc = new RecordingTournamentClient
    val h = handler("white", Some("d2d4"), tournamentClient = tc)
    val move = MoveInfo("e2e4", "fen2", turn = "black")
    h.handleEvent(GameStreamEvent(move = Some(move)))
    tc.submittedMoves shouldBe empty

  // ── handleEvent — gameEnd ───────────────────────────────────────────────────

  it should "handle a win gameEnd without submitting moves" in:
    val tc = new RecordingTournamentClient
    val h = handler("white", tournamentClient = tc)
    val end = GameEndInfo(winner = Some("white"), status = "checkmate")
    h.handleEvent(GameStreamEvent(gameEnd = Some(end)))
    tc.submittedMoves shouldBe empty

  it should "handle a loss gameEnd" in:
    val tc = new RecordingTournamentClient
    val h = handler("white", tournamentClient = tc)
    val end = GameEndInfo(winner = Some("black"), status = "checkmate")
    h.handleEvent(GameStreamEvent(gameEnd = Some(end)))
    tc.submittedMoves shouldBe empty

  it should "handle a draw gameEnd (no winner)" in:
    val tc = new RecordingTournamentClient
    val h = handler("white", tournamentClient = tc)
    val end = GameEndInfo(winner = None, status = "stalemate")
    h.handleEvent(GameStreamEvent(gameEnd = Some(end)))
    tc.submittedMoves shouldBe empty

  // ── Bot failure ─────────────────────────────────────────────────────────────

  it should "not crash when bot-service returns no move" in:
    val tc = new RecordingTournamentClient
    val h = handler("white", botClient = new FailingBotClient, tournamentClient = tc)
    val state = GameStateInfo("fen1", turn = "white", status = "ongoing")
    h.handleEvent(GameStreamEvent(gameState = Some(state)))
    tc.submittedMoves shouldBe empty

  it should "not crash when move submission fails" in:
    val tc = new FailingTournamentClient
    val h = handler("white", tournamentClient = tc)
    val state = GameStateInfo("fen1", turn = "white", status = "ongoing")
    noException should be thrownBy h.handleEvent(GameStreamEvent(gameState = Some(state)))

  // ── Empty event ─────────────────────────────────────────────────────────────

  it should "handle an empty event without error" in:
    val tc = new RecordingTournamentClient
    val h = handler(tournamentClient = tc)
    noException should be thrownBy h.handleEvent(GameStreamEvent())
    tc.submittedMoves shouldBe empty
