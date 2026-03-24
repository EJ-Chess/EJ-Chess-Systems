// core/src/main/scala/de/nowchess/chess/controller/CommandParser.scala
package de.nowchess.chess.controller

import de.nowchess.chess.model.Square
import scala.util.matching.Regex

object CommandParser:
  private val squareRegex: Regex = "^[a-h][1-8]$".r

  def parse(input: String): Either[String, (Square, Square)] =
    val tokens = input.trim.split("\\s+").toList.filter(_.nonEmpty)
    if tokens.length == 2 && tokens.forall(squareRegex.matches) then
      Right((toSquare(tokens(0)), toSquare(tokens(1))))
    else
      Left("Invalid command format. Use: <from> <to> (e.g. e2 e4)")

  private def toSquare(token: String): Square =
    Square(token(0) - 'a', token(1) - '1')
