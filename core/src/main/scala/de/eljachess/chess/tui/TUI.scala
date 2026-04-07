package de.eljachess.chess.tui

import de.eljachess.chess.controller.{GameController, GameManager, Observer}
import de.eljachess.chess.model.{Fen, Json}
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
          case s if s.startsWith("save-json ") =>
            val filename = trimmed.drop("save-json ".length).trim
            try
              java.nio.file.Files.write(
                java.nio.file.Paths.get(filename),
                Json.encode(manager.state).getBytes("UTF-8")
              )
              s"Saved to $filename"
            catch
              case e: java.io.IOException => s"Error: ${e.getMessage}"
          case s if s.startsWith("load-json ") =>
            val filename = trimmed.drop("load-json ".length).trim
            try
              val content = java.nio.file.Files.readString(
                java.nio.file.Paths.get(filename),
                java.nio.charset.StandardCharsets.UTF_8
              )
              Json.decode(content) match
                case Left(err)   => s"JSON error: $err"
                case Right(ctrl) =>
                  manager.move(s"load ${Fen.encode(ctrl)}", this)
                  s"Loaded from $filename"
            catch
              case e: java.io.IOException => s"Error: ${e.getMessage}"
          case _ => manager.move(trimmed, this)
        println(msg)
      loop() // always recurse while input is non-null; empty lines skip the move
