package de.eljachess.chess

import de.eljachess.chess.controller.{GameController, GameManager}
import de.eljachess.chess.model.Board
import de.eljachess.chess.tui.TUI
import java.io.PrintStream

@main def main(): Unit =
  System.setOut(PrintStream(System.out, true, "UTF-8"))
  val manager = GameManager(GameController(Board.initial))
  TUI(manager).start()
