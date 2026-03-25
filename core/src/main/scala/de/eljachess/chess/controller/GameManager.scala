package de.eljachess.chess.controller

import scala.collection.mutable

class GameManager(initial: GameController):
  private var current   = initial
  private var history   = List.empty[GameController]
  private var future    = List.empty[GameController]
  private val observers = mutable.Buffer.empty[Observer]

  def addObserver(o: Observer): Unit = synchronized { observers += o }
  def state: GameController           = synchronized { current }

  def move(input: String, caller: Observer | Null = null): String = synchronized {
    val (next, msg) = current.handleCommand(input)
    if next != current then
      history = current :: history
      future  = Nil
      current = next
      notifyObservers(msg, skip = caller)
    msg
  }

  def undo(caller: Observer | Null = null): String = synchronized {
    history match
      case Nil          => "Nothing to undo"
      case prev :: rest =>
        future  = current :: future
        history = rest
        current = prev
        notifyObservers("Undo", skip = caller)
        "Undo"
  }

  def redo(caller: Observer | Null = null): String = synchronized {
    future match
      case Nil          => "Nothing to redo"
      case next :: rest =>
        history = current :: history
        future  = rest
        current = next
        notifyObservers("Redo", skip = caller)
        "Redo"
  }

  // Called only from within synchronized methods — do NOT call manager methods from observer.onUpdate
  private def notifyObservers(msg: String, skip: Observer | Null): Unit =
    observers.foreach(o => if o ne skip then o.onUpdate(current, msg))
