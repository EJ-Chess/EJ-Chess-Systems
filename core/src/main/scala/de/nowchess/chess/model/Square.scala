// core/src/main/scala/de/nowchess/chess/model/Square.scala
package de.nowchess.chess.model

case class Square(col: Int, row: Int):
  def toAlgebraic: String = s"${('a' + col).toChar}${row + 1}"
