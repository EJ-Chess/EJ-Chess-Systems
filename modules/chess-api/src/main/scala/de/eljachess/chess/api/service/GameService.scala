package de.eljachess.chess.api.service

import de.eljachess.chess.controller.{GameController, GameManager, SanDecoder}
import de.eljachess.chess.model.{Board, Color, Fen, PieceKind, Pgn, Square}
import de.eljachess.chess.api.dto.{GameStateResponse, MoveNotation}
import de.eljachess.chess.api.exception.GameNotFoundException
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import scala.collection.mutable

@ApplicationScoped
class GameService:

  private val games: mutable.Map[String, GameManager] = mutable.Map.empty

  // ── Public API ─────────────────────────────────────────────────────────────

  def createGame(): String =
    val id      = UUID.randomUUID().toString
    val initial = GameController(Board.initial)
    val manager = GameManager(initial)
    games(id)   = manager
    id

  def makeMoveAlgebraic(
    gameId:    String,
    from:      String,
    to:        String,
    promotion: Option[String]
  ): Either[String, Unit] =
    val manager = getGameOrThrow(gameId)
    parseSquare(from) match
      case None =>
        Left(s"Invalid square: '$from'")
      case Some(_) =>
        parseSquare(to) match
          case None =>
            Left(s"Invalid square: '$to'")
          case Some(_) =>
            val promoSuffix = promotion
              .flatMap(p => parsePromotion(p))
              .map(k => s"=${pieceKindToChar(k)}")
              .getOrElse("")
            val command = s"$from$to$promoSuffix"
            val result  = manager.move(command)
            if result.startsWith("Invalid") || result.startsWith("No piece") || result.startsWith("It's") then
              Left(result)
            else
              Right(())

  def makeMoveSan(gameId: String, san: String): Either[String, Unit] =
    val manager = getGameOrThrow(gameId)
    val ctrl    = manager.state
    SanDecoder.expand(ctrl.board, ctrl.currentTurn, san) match
      case Left(err) =>
        Left(err)
      case Right((from, to, promo)) =>
        val promoSuffix = promo.map(k => s"=${pieceKindToChar(k)}").getOrElse("")
        val command     = s"${from.toAlgebraic}${to.toAlgebraic}$promoSuffix"
        val result      = manager.move(command)
        if result.startsWith("Invalid") || result.startsWith("No piece") || result.startsWith("It's") then
          Left(result)
        else
          Right(())

  def importPgn(gameId: String, pgnString: String): Either[String, Unit] =
    val manager = getGameOrThrow(gameId)
    Pgn.decode(pgnString) match
      case Left(err) =>
        Left(err)
      case Right((_, moves)) =>
        val newManager = GameManager(GameController(Board.initial))
        val errors = moves.flatMap { san =>
          val ctrl = newManager.state
          SanDecoder.expand(ctrl.board, ctrl.currentTurn, san) match
            case Left(err) =>
              Some(s"Failed to parse SAN '$san': $err")
            case Right((from, to, promo)) =>
              val promoSuffix = promo.map(k => s"=${pieceKindToChar(k)}").getOrElse("")
              val command     = s"${from.toAlgebraic}${to.toAlgebraic}$promoSuffix"
              val result      = newManager.move(command)
              if result.startsWith("Invalid") || result.startsWith("No piece") || result.startsWith("It's") then
                Some(s"Move '$san' failed: $result")
              else
                None
        }
        errors.headOption match
          case Some(err) => Left(err)
          case None =>
            games(gameId) = newManager
            Right(())

  def importFen(gameId: String, fenString: String): Either[String, Unit] =
    getGameOrThrow(gameId)
    Fen.decode(fenString) match
      case Left(err) =>
        Left(err)
      case Right(ctrl) =>
        games(gameId) = GameManager(ctrl)
        Right(())

  def undo(gameId: String): Either[String, String] =
    val manager = getGameOrThrow(gameId)
    val result  = manager.undo()
    if result == "Nothing to undo" then
      Left(result)
    else
      Right(Fen.encode(manager.state))

  def redo(gameId: String): Either[String, String] =
    val manager = getGameOrThrow(gameId)
    val result  = manager.redo()
    if result == "Nothing to redo" then
      Left(result)
    else
      Right(Fen.encode(manager.state))

  def getGameState(gameId: String): Either[String, GameStateResponse] =
    val manager = getGameOrThrow(gameId)
    val ctrl    = manager.state
    val board   = ctrl.board
    val color   = ctrl.currentTurn
    val legalMs = board.legalMoves(color)
    val inCheck      = board.isInCheck(color)
    val hasLegalMoves = legalMs.nonEmpty
    val inCheckmate  = inCheck && !hasLegalMoves
    val inStalemate  = !inCheck && !hasLegalMoves
    Right(GameStateResponse(
      gameId         = gameId,
      fen            = Fen.encode(ctrl),
      currentTurn    = if color == Color.White then "WHITE" else "BLACK",
      fullmoveNumber = ctrl.fullmoveNumber,
      halfmoveClock  = ctrl.halfmoveClock,
      inCheck        = inCheck,
      inCheckmate    = inCheckmate,
      inStalemate    = inStalemate,
      legalMovesCount = legalMs.size
    ))

  def getLegalMoves(gameId: String): Either[String, List[MoveNotation]] =
    val manager = getGameOrThrow(gameId)
    val ctrl    = manager.state
    val moves   = ctrl.board.legalMoves(ctrl.currentTurn).map { case (from, to) =>
      MoveNotation(from = from.toAlgebraic, to = to.toAlgebraic, promotion = None)
    }
    Right(moves)

  def deleteGame(gameId: String): Either[String, Unit] =
    getGameOrThrow(gameId)
    games.remove(gameId)
    Right(())

  // ── Private helpers ────────────────────────────────────────────────────────

  private def getGameOrThrow(gameId: String): GameManager =
    games.getOrElse(gameId, throw GameNotFoundException(gameId))

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
