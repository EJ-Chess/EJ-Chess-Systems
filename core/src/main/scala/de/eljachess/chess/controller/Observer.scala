package de.eljachess.chess.controller

trait Observer:
  def onUpdate(ctrl: GameController, message: String): Unit
