package de.eljachess.chess.tui

import de.eljachess.chess.controller.GameController
import scala.io.StdIn
import scala.annotation.tailrec

class TUI(initialController: GameController):

  def start(): Unit = loop(initialController)

  @tailrec
  private def loop(ctrl: GameController): Unit =
    println(Renderer.render(ctrl.board, ctrl.currentTurn))
    val line = StdIn.readLine()
    if line != null then
      if line.trim.nonEmpty then
        val (nextCtrl, msg) = ctrl.handleCommand(line)
        println(msg)
        loop(nextCtrl)
      else
        loop(ctrl)
