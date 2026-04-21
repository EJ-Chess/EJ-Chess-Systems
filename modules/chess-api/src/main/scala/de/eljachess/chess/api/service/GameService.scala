package de.eljachess.chess.api.service

import de.eljachess.chess.api.client.BotClient
import de.eljachess.chess.api.dto.{CreateGameRequest, GameStateResponse, MoveNotation}
import de.eljachess.chess.controller.{GameController, GameManager, SanDecoder}
import de.eljachess.chess.model.{Board, Color, Fen, PieceKind, Pgn, Square}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID
import scala.collection.concurrent.TrieMap
import scala.collection.mutable

/** Per-game bot configuration (stored alongside the GameManager). */
private case class BotConfig(botColor: Color, elo: Int)

@ApplicationScoped
class GameService:

  /** Injected by CDI; null when instantiated directly in unit tests (guarded below). */
  @Inject
  var botClient: BotClient = _

  private val games:      mutable.Map[String, GameManager] = mutable.Map.empty
  private val botConfigs: TrieMap[String, BotConfig]       = TrieMap.empty

  // ── Public API ─────────────────────────────────────────────────────────────

  def createGame(request: CreateGameRequest = CreateGameRequest()): String =
    val id          = UUID.randomUUID().toString
    val playerColor = if request.playerColor.exists(_.toLowerCase == "black") then Color.Black else Color.White
    val ctrl        = GameController(Board.initial)
    val manager     = GameManager(ctrl)
    games(id) = manager

    request.opponent.map(_.toLowerCase) match
      case Some("bot") =>
        val elo      = request.botElo.getOrElse(1400)
        val botColor = if playerColor == Color.White then Color.Black else Color.White
        botConfigs(id) = BotConfig(botColor, elo)
        // If bot plays White (player chose Black) it makes the opening move immediately
        if botColor == Color.White then
          applyBotMoveIfNeeded(id, manager)
      case _ => ()

    id

  def makeMoveAlgebraic(
    gameId:    String,
    from:      String,
    to:        String,
    promotion: Option[String]
  ): Either[String, Unit] =
    for
      manager <- findGame(gameId)
      _       <- parseSquare(from).toRight(s"Invalid square: '$from'")
      _       <- parseSquare(to).toRight(s"Invalid square: '$to'")
    yield
      val promoSuffix = promotion
        .flatMap(p => parsePromotion(p))
        .map(k => s" ${pieceKindToChar(k)}")
        .getOrElse("")
      val command = s"$from $to$promoSuffix"
      val result  = manager.move(command)
      if result.startsWith("Invalid") || result.startsWith("No piece") || result.startsWith("It's") then
        return Left(result)
      applyBotMoveIfNeeded(gameId, manager)

  def makeMoveSan(gameId: String, san: String): Either[String, Unit] =
    for
      manager        <- findGame(gameId)
      (from, to, pr) <- SanDecoder.expand(manager.state.board, manager.state.currentTurn, san)
    yield
      val promoSuffix = pr.map(k => s" ${pieceKindToChar(k)}").getOrElse("")
      val command     = s"${from.toAlgebraic} ${to.toAlgebraic}$promoSuffix"
      val result      = manager.move(command)
      if result.startsWith("Invalid") || result.startsWith("No piece") || result.startsWith("It's") then
        return Left(result)
      applyBotMoveIfNeeded(gameId, manager)

  def importPgn(gameId: String, pgnString: String): Either[String, Unit] =
    for
      _          <- findGame(gameId)
      (_, moves) <- Pgn.decode(pgnString)
    yield
      val newManager = GameManager(GameController(Board.initial))
      val errors = moves.flatMap { san =>
        val ctrl = newManager.state
        SanDecoder.expand(ctrl.board, ctrl.currentTurn, san) match
          case Left(err) =>
            Some(s"Failed to parse SAN '$san': $err")
          case Right((from, to, promo)) =>
            val promoSuffix = promo.map(k => s" ${pieceKindToChar(k)}").getOrElse("")
            val command     = s"${from.toAlgebraic} ${to.toAlgebraic}$promoSuffix"
            val result      = newManager.move(command)
            if result.startsWith("Invalid") || result.startsWith("No piece") || result.startsWith("It's") then
              Some(s"Move '$san' failed: $result")
            else
              None
      }
      errors.headOption match
        case Some(err) => return Left(err)
        case None      => games(gameId) = newManager

  def importFen(gameId: String, fenString: String): Either[String, Unit] =
    for
      _    <- findGame(gameId)
      ctrl <- Fen.decode(fenString)
    yield
      games(gameId) = GameManager(ctrl)

  def undo(gameId: String): Either[String, String] =
    findGame(gameId).flatMap { manager =>
      val result = manager.undo()
      if result == "Nothing to undo" then Left(result)
      else Right(Fen.encode(manager.state))
    }

  def redo(gameId: String): Either[String, String] =
    findGame(gameId).flatMap { manager =>
      val result = manager.redo()
      if result == "Nothing to redo" then Left(result)
      else Right(Fen.encode(manager.state))
    }

  def getGameState(gameId: String): Either[String, GameStateResponse] =
    findGame(gameId).map { manager =>
      val ctrl          = manager.state
      val board         = ctrl.board
      val color         = ctrl.currentTurn
      val legalMs       = board.legalMoves(color)
      val inCheck       = board.isInCheck(color)
      val hasLegalMoves = legalMs.nonEmpty
      GameStateResponse(
        gameId          = gameId,
        fen             = Fen.encode(ctrl),
        currentTurn     = if color == Color.White then "WHITE" else "BLACK",
        fullmoveNumber  = ctrl.fullmoveNumber,
        halfmoveClock   = ctrl.halfmoveClock,
        inCheck         = inCheck,
        inCheckmate     = inCheck && !hasLegalMoves,
        inStalemate     = !inCheck && !hasLegalMoves,
        legalMovesCount = legalMs.size
      )
    }

  def getLegalMoves(gameId: String): Either[String, List[MoveNotation]] =
    findGame(gameId).map { manager =>
      val ctrl = manager.state
      ctrl.board.legalMoves(ctrl.currentTurn).map { case (from, to) =>
        MoveNotation(from = from.toAlgebraic, to = to.toAlgebraic, promotion = None)
      }
    }

  def getPgn(gameId: String): Either[String, String] =
    findGame(gameId).map(_.pgn("White", "Black"))

  def getManager(gameId: String): Either[String, GameManager] =
    findGame(gameId)

  def deleteGame(gameId: String): Either[String, Unit] =
    findGame(gameId).map { _ =>
      games.remove(gameId)
      botConfigs.remove(gameId)
    }

  // ── Bot integration ────────────────────────────────────────────────────────

  /**
   * If this is a bot game and it is the bot's turn, delegate to BotClient
   * (which is protected by @CircuitBreaker + @Timeout + @Fallback).
   * Silently skipped when botClient is null (unit-test context without CDI).
   */
  private def applyBotMoveIfNeeded(gameId: String, manager: GameManager): Unit =
    if botClient == null then return
    for config <- botConfigs.get(gameId) do
      val ctrl = manager.state
      if ctrl.currentTurn == config.botColor then
        val fen      = Fen.encode(ctrl)
        val colorStr = if config.botColor == Color.White then "white" else "black"
        botClient.fetchMove(fen, colorStr, config.elo) match
          case Some((from, to)) => manager.move(s"$from $to")
          case None             => () // no legal moves or service unavailable / circuit open

  // ── Private helpers ────────────────────────────────────────────────────────

  private def findGame(gameId: String): Either[String, GameManager] =
    games.get(gameId).toRight(s"Game not found: $gameId")

  private def parseSquare(s: String): Option[Square] =
    if s.length == 2 && s(0) >= 'a' && s(0) <= 'h' && s(1) >= '1' && s(1) <= '8' then
      Some(Square(s(0) - 'a', s(1) - '1'))
    else
      None

  private def parsePromotion(p: String): Option[PieceKind] = p.toUpperCase match
    case "Q" => Some(PieceKind.Queen)
    case "R" => Some(PieceKind.Rook)
    case "B" => Some(PieceKind.Bishop)
    case "N" => Some(PieceKind.Knight)
    case _   => None

  private def pieceKindToChar(k: PieceKind): Char = k match
    case PieceKind.Queen  => 'Q'
    case PieceKind.Rook   => 'R'
    case PieceKind.Bishop => 'B'
    case PieceKind.Knight => 'N'
    case PieceKind.King   => 'K'
    case PieceKind.Pawn   => 'P'
