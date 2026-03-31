// core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.{GameController, ParsedMove}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PgnSpec extends AnyFlatSpec with Matchers:

  "Pgn.encode" should "include 7-tag header with provided player names" in {
    val headers = Pgn.encode(List(), "Alice", "Bob", GameController(Board.initial))
    headers should include("[White \"Alice\"]")
    headers should include("[Black \"Bob\"]")
    headers should include("[Event \"?\"]")
    headers should include("[Site \"?\"]")
    headers should include("[Round \"?\"]")
  }

  it should "include today's date in YYYY.MM.DD format" in {
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    val headers = Pgn.encode(List(), "White", "Black", GameController(Board.initial))
    headers should include(s"[Date \"$today\"]")
  }

  it should "detect in-progress game as result *" in {
    val headers = Pgn.encode(List(), "White", "Black", GameController(Board.initial))
    headers should include("[Result \"*\"]")
  }

  it should "detect checkmate as 1-0 when Black to move and checkmated" is (pending)

  it should "detect stalemate as 1/2-1/2" is (pending)
