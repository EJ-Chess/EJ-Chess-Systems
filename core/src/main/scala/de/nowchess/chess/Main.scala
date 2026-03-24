package de.nowchess.chess

import de.nowchess.chess.controller.GameController
import de.nowchess.chess.model.Board
import de.nowchess.chess.tui.TUI

@main def main(): Unit =
  TUI(GameController(Board.initial)).start()
