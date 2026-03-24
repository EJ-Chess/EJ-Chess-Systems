// core/src/main/scala/de/eljachess/chess/model/Square.scala
package de.eljachess.chess.model

case class Square(col: Int, row: Int):
  def toAlgebraic: String = s"${('a' + col).toChar}${row + 1}"

object Square:
  val all: List[Square] = (for col <- 0 to 7; row <- 0 to 7 yield Square(col, row)).toList
