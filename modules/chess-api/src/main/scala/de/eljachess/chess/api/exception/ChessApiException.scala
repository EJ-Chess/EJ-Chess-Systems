package de.eljachess.chess.api.exception

sealed trait ChessApiException extends Exception:
  def gameId: Option[String]
  def userMessage: String
  def technicalDetails: Option[String]

case class GameNotFoundException(
  id: String,
  override val technicalDetails: Option[String] = None
) extends ChessApiException:
  override def gameId: Option[String] = Some(id)
  override def getMessage: String = userMessage
  override val userMessage: String = s"Game not found: $id"

case class InvalidMoveException(
  id: String,
  reason: String,
  legalMoves: List[String],
  override val technicalDetails: Option[String] = None
) extends ChessApiException:
  override def gameId: Option[String] = Some(id)
  override def getMessage: String = userMessage
  override val userMessage: String = s"Invalid move: $reason"

case class InvalidNotationException(
  notation: String,
  override val technicalDetails: Option[String] = None
) extends ChessApiException:
  override def gameId: Option[String] = None
  override def getMessage: String = userMessage
  override val userMessage: String = s"Invalid notation: '$notation' is not a valid square"

case class InvalidPgnFenException(
  input: String,
  parseError: String,
  override val technicalDetails: Option[String] = None
) extends ChessApiException:
  override def gameId: Option[String] = None
  override def getMessage: String = userMessage
  override val userMessage: String = s"Invalid PGN/FEN: $parseError"

case class InvalidGameStateException(
  id: String,
  reason: String,
  override val technicalDetails: Option[String] = None
) extends ChessApiException:
  override def gameId: Option[String] = Some(id)
  override def getMessage: String = userMessage
  override val userMessage: String = s"Invalid game state: $reason"
