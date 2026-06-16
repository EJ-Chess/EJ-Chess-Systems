package de.eljachess.chess.api.service

import de.eljachess.chess.api.dto.{CreateGameRequest, GameStateResponse, MoveNotation}
import de.eljachess.chess.api.kafka.BotMoveKafkaProducer
import de.eljachess.chess.api.persistence.{GameRepository, GameRow}
import de.eljachess.chess.controller.{GameController, GameManager, SanDecoder}
import de.eljachess.chess.model.{Board, Color, Fen, PieceKind, Pgn, Square}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import scala.compiletime.uninitialized
import java.util.UUID
import scala.collection.concurrent.TrieMap

/** Per-game bot configuration (stored alongside the GameManager). */
private case class BotConfig(botColor: Color, elo: Int)

@ApplicationScoped
class GameService:

  /** Injected by CDI; null when instantiated directly in unit tests (guarded below). */
  @Inject var kafkaProducer: BotMoveKafkaProducer = uninitialized

  /** Null in unit tests (no CDI). All persist/load calls are guarded. */
  @Inject var repository: GameRepository = uninitialized

  private val games:      TrieMap[String, GameManager] = TrieMap.empty
  private val botConfigs: TrieMap[String, BotConfig]       = TrieMap.empty

  // ── Public API ─────────────────────────────────────────────────────────────

  def createGame(request: CreateGameRequest = CreateGameRequest()): String =
    val id          = UUID.randomUUID().toString
    val playerColor = if request.playerColor.exists(_.toLowerCase == "black") then Color.Black else Color.White
    val ctrl        = GameController(Board.initial)
    val manager     = GameManager(ctrl)
    games(id) = manager

    val (botColorOpt, botEloOpt) = request.opponent.map(_.toLowerCase) match
      case Some("bot") =>
        val elo      = request.botElo.getOrElse(1400)
        val botColor = if playerColor == Color.White then Color.Black else Color.White
        botConfigs(id) = BotConfig(botColor, elo)
        if botColor == Color.White then applyBotMoveIfNeeded(id, manager)
        (Some(if botColor == Color.White then "WHITE" else "BLACK"), Some(elo))
      case _ => (None, None)

    persistGame(id, manager, if playerColor == Color.White then "WHITE" else "BLACK", botColorOpt, botEloOpt)

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
      val promoSuffix = promotion.flatMap(parsePromotion).map(k => s" ${pieceKindToChar(k)}").getOrElse("")
      val result      = manager.move(s"$from $to$promoSuffix")
      if result.startsWith("Invalid") || result.startsWith("No piece") || result.startsWith("It's") then
        return Left(result)
      applyBotMoveIfNeeded(gameId, manager)
      updatePgn(gameId, manager)

  def makeMoveSan(gameId: String, san: String): Either[String, Unit] =
    for
      manager        <- findGame(gameId)
      (from, to, pr) <- SanDecoder.expand(manager.state.board, manager.state.currentTurn, san)
    yield
      val promoSuffix = pr.map(k => s" ${pieceKindToChar(k)}").getOrElse("")
      val result      = manager.move(s"${from.toAlgebraic} ${to.toAlgebraic}$promoSuffix")
      if result.startsWith("Invalid") || result.startsWith("No piece") || result.startsWith("It's") then
        return Left(result)
      applyBotMoveIfNeeded(gameId, manager)
      updatePgn(gameId, manager)

  def importPgn(gameId: String, pgnString: String): Either[String, Unit] =
    for
      _          <- findGame(gameId)
      (_, moves) <- Pgn.decode(pgnString)
    yield
      val newManager = GameManager(GameController(Board.initial))
      val errors = moves.flatMap { san =>
        val ctrl = newManager.state
        SanDecoder.expand(ctrl.board, ctrl.currentTurn, san) match
          case Left(err) => Some(s"Failed to parse SAN '$san': $err")
          case Right((from, to, promo)) =>
            val promoSuffix = promo.map(k => s" ${pieceKindToChar(k)}").getOrElse("")
            val result      = newManager.move(s"${from.toAlgebraic} ${to.toAlgebraic}$promoSuffix")
            if result.startsWith("Invalid") || result.startsWith("No piece") || result.startsWith("It's") then
              Some(s"Move '$san' failed: $result")
            else None
      }
      errors.headOption match
        case Some(err) => return Left(err)
        case None =>
          games(gameId) = newManager
          updatePgn(gameId, newManager)

  def importFen(gameId: String, fenString: String): Either[String, Unit] =
    for
      _    <- findGame(gameId)
      ctrl <- Fen.decode(fenString)
    yield
      val newManager = GameManager(ctrl)
      games(gameId) = newManager
      updatePgn(gameId, newManager)

  def undo(gameId: String): Either[String, String] =
    findGame(gameId).flatMap { manager =>
      val result = manager.undo()
      if result == "Nothing to undo" then Left(result)
      else
        updatePgn(gameId, manager)
        Right(Fen.encode(manager.state))
    }

  def redo(gameId: String): Either[String, String] =
    findGame(gameId).flatMap { manager =>
      val result = manager.redo()
      if result == "Nothing to redo" then Left(result)
      else
        updatePgn(gameId, manager)
        Right(Fen.encode(manager.state))
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
      if repository != null then repository.delete(gameId)
    }

  // ── Persistence helpers ────────────────────────────────────────────────────

  private def persistGame(
    id:          String,
    manager:     GameManager,
    playerColor: String,
    botColor:    Option[String],
    botElo:      Option[Int]
  ): Unit =
    if repository == null then return
    repository.insert(GameRow(
      id          = id,
      pgn         = manager.pgn("White", "Black"),
      playerColor = playerColor,
      botColor    = botColor,
      botElo      = botElo
    ))

  private def updatePgn(gameId: String, manager: GameManager): Unit =
    if repository == null then return
    repository.updatePgn(gameId, manager.pgn("White", "Black"))

  private def findGame(gameId: String): Either[String, GameManager] =
    games.get(gameId) match
      case Some(m) => Right(m)
      case None    => loadFromDb(gameId)

  private def loadFromDb(gameId: String): Either[String, GameManager] =
    if repository == null then return Left(s"Game not found: $gameId")
    repository.findById(gameId) match
      case None => Left(s"Game not found: $gameId")
      case Some(row) =>
        val manager = GameManager(GameController(Board.initial))
        if row.pgn.nonEmpty then
          Pgn.decode(row.pgn) match
            case Left(_) => ()
            case Right((_, moves)) =>
              moves.foreach { san =>
                val ctrl = manager.state
                SanDecoder.expand(ctrl.board, ctrl.currentTurn, san).foreach { case (from, to, promo) =>
                  val promoSuffix = promo.map(k => s" ${pieceKindToChar(k)}").getOrElse("")
                  manager.move(s"${from.toAlgebraic} ${to.toAlgebraic}$promoSuffix")
                }
              }
        for botColor <- row.botColor; elo <- row.botElo do
          val color = if botColor == "WHITE" then Color.White else Color.Black
          botConfigs(gameId) = BotConfig(color, elo)
        games(gameId) = manager
        Right(manager)

  // ── Bot integration ────────────────────────────────────────────────────────

  /**
   * Publish a bot move request to Kafka (chess.move-requests).
   *
   * Non-blocking: returns immediately; bot-service will compute the move and
   * publish the result to chess.bot-responses, which BotMoveKafkaConsumer
   * picks up and routes to applyBotMoveAsync.
   *
   * No-op in unit tests where kafkaProducer is null (no CDI).
   */
  private def applyBotMoveIfNeeded(gameId: String, manager: GameManager): Unit =
    if kafkaProducer == null then return
    for config <- botConfigs.get(gameId) do
      val ctrl = manager.state
      if ctrl.currentTurn == config.botColor then
        val fen      = Fen.encode(ctrl)
        val colorStr = if config.botColor == Color.White then "white" else "black"
        kafkaProducer.publishMoveRequest(gameId, fen, colorStr, config.elo)

  /**
   * Apply a bot move that arrived asynchronously via Kafka (chess.bot-responses).
   *
   * Called by BotMoveKafkaConsumer on the poller thread.
   * Silently ignored if the game has already ended or been deleted.
   */
  def applyBotMoveAsync(gameId: String, from: String, to: String): Unit =
    games.get(gameId).foreach { manager =>
      manager.move(s"$from $to")
      updatePgn(gameId, manager)
    }

  // ── Private helpers ────────────────────────────────────────────────────────

  private def parseSquare(s: String): Option[Square] =
    if s.length == 2 && s(0) >= 'a' && s(0) <= 'h' && s(1) >= '1' && s(1) <= '8' then
      Some(Square(s(0) - 'a', s(1) - '1'))
    else None

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
