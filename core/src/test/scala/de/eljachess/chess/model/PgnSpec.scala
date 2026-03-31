// core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.{GameController, ParsedMove}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PgnSpec extends AnyFlatSpec with Matchers:

  "Pgn.encode" should "include 7-tag header with provided player names" in {
    val pgn = Pgn.encode(List(), "Alice", "Bob", GameController(Board.initial))
    pgn should include("[White \"Alice\"]")
    pgn should include("[Black \"Bob\"]")
    pgn should include("[Event \"?\"]")
    pgn should include("[Site \"?\"]")
    pgn should include("[Round \"?\"]")
  }

  it should "include today's date in YYYY.MM.DD format" in {
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    val pgn = Pgn.encode(List(), "White", "Black", GameController(Board.initial))
    pgn should include(s"[Date \"$today\"]")
  }

  it should "detect in-progress game as result *" in {
    val pgn = Pgn.encode(List(), "White", "Black", GameController(Board.initial))
    pgn should include("[Result \"*\"]")
    pgn should endWith("*")
  }

  it should "detect checkmate as 1-0 when Black to move and checkmated" is (pending)

  it should "detect stalemate as 1/2-1/2" is (pending)
