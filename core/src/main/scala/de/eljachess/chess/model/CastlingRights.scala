// core/src/main/scala/de/eljachess/chess/model/CastlingRights.scala
package de.eljachess.chess.model

case class CastlingRights(
  whiteKingside:  Boolean = true,
  whiteQueenside: Boolean = true,
  blackKingside:  Boolean = true,
  blackQueenside: Boolean = true
)
