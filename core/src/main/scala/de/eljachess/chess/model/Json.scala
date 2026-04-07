// core/src/main/scala/de/eljachess/chess/model/Json.scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.GameController
import java.time.LocalDate

object Json:

  def encode(ctrl: GameController, whiteName: String = "White", blackName: String = "Black"): String =
    val fen  = Fen.encode(ctrl)
    val date = LocalDate.now().toString
    s"""{
  "fen": "$fen",
  "whiteName": "$whiteName",
  "blackName": "$blackName",
  "date": "$date"
}"""

  val decode: String => Either[String, GameController] = jsonStr =>
    val fenPattern = """"fen"\s*:\s*"([^"]*)"""".r
    fenPattern.findFirstMatchIn(jsonStr) match
      case None    => Left("Invalid JSON: missing field 'fen'")
      case Some(m) => Fen.decode(m.group(1))
