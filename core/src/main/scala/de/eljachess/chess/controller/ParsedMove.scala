// core/src/main/scala/de/eljachess/chess/controller/ParsedMove.scala
package de.eljachess.chess.controller

import de.eljachess.chess.model.{PieceKind, Square}

enum ParsedMove:
  case Move(from: Square, to: Square, promotion: Option[PieceKind])
  case Castling(kingside: Boolean)
