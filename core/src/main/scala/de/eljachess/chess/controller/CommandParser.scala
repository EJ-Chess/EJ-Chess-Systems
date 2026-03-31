// core/src/main/scala/de/eljachess/chess/controller/CommandParser.scala
package de.eljachess.chess.controller

import de.eljachess.chess.model.{PieceKind, Square}
import scala.util.matching.Regex

object CommandParser:
  private val squareRegex: Regex = "^[a-h][1-8]$".r

  def parse(input: String): Either[String, ParsedMove] =
    val trimmed = input.trim
    if trimmed == "fen" then return Right(ParsedMove.FenQuery)
    if trimmed.startsWith("load ") then
      val fen = trimmed.stripPrefix("load ").trim
      if fen.nonEmpty then return Right(ParsedMove.FenLoad(fen))
      else return Left("Usage: load <fen>")
    if trimmed == "load" then return Left("Usage: load <fen>")
    if trimmed == "O-O-O" then return Right(ParsedMove.Castling(kingside = false))
    if trimmed == "O-O"   then return Right(ParsedMove.Castling(kingside = true))

    val tokens = trimmed.split("\\s+").toList.filter(_.nonEmpty)
    tokens match
      case List(f, t) if squareRegex.matches(f) && squareRegex.matches(t) =>
        Right(ParsedMove.Move(toSquare(f), toSquare(t), None))
      case List(f, t, p) if squareRegex.matches(f) && squareRegex.matches(t) =>
        parsePromotion(p) match
          case Some(kind) => Right(ParsedMove.Move(toSquare(f), toSquare(t), Some(kind)))
          case None       => Left("Invalid command format. Use: <from> <to> (e.g. e2 e4)")
      case _ =>
        Left("Invalid command format. Use: <from> <to> (e.g. e2 e4)")

  private def toSquare(token: String): Square =
    Square(token(0) - 'a', token(1) - '1')

  private def parsePromotion(token: String): Option[PieceKind] = token match
    case "Q" => Some(PieceKind.Queen)
    case "R" => Some(PieceKind.Rook)
    case "B" => Some(PieceKind.Bishop)
    case "N" => Some(PieceKind.Knight)
    case _   => None
