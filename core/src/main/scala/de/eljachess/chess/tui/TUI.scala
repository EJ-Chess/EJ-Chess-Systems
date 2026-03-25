package de.eljachess.chess.tui

import de.eljachess.chess.controller.{GameController, GameManager, Observer}
import scala.annotation.tailrec

class TUI(manager: GameManager, readLine: () => String | Null = () => scala.io.StdIn.readLine())
    extends Observer:

  def start(): Unit =
    manager.addObserver(this)
    loop()

  def onUpdate(ctrl: GameController, msg: String): Unit =
    // Invoked only for moves originating from another observer (e.g. the GUI)
    println(s"\n[GUI] $msg")
    println(Renderer.render(ctrl.board, ctrl.currentTurn))

  @tailrec
  private def loop(): Unit =
    val ctrl = manager.state
    println(Renderer.render(ctrl.board, ctrl.currentTurn))
    val line = readLine()
    if line != null then
      val trimmed = line.trim
      if trimmed.nonEmpty then
        val msg = trimmed.toLowerCase match
          case "undo" => manager.undo(this)
          case "redo" => manager.redo(this)
          case _      => manager.move(trimmed, this)
        println(msg)
      loop() // always recurse while input is non-null; empty lines skip the move
