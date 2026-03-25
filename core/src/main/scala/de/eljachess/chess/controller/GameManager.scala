package de.eljachess.chess.controller

import scala.collection.mutable

class GameManager(initial: GameController):
  private var current   = initial
  private var history   = List.empty[GameController]
  private var future    = List.empty[GameController]
  private val observers = mutable.Buffer.empty[Observer]

  def addObserver(o: Observer): Unit = synchronized {
    if !observers.contains(o) then observers += o
  }
  def state: GameController           = synchronized { current }

  def move(input: String, caller: Observer | Null = null): String =
    val (snapshot, ctrl, msg) = synchronized {
      val (next, msg) = current.handleCommand(input)
      if next != current then
        history = current :: history
        future  = Nil
        current = next
        (observers.toList.filterNot(_ eq caller), current, msg)
      else
        (Nil, current, msg)
    }
    snapshot.foreach(_.onUpdate(ctrl, msg))
    msg

  def undo(caller: Observer | Null = null): String =
    val result = synchronized {
      history match
        case Nil          => (Nil, current, "Nothing to undo")
        case prev :: rest =>
          future  = current :: future
          history = rest
          current = prev
          (observers.toList.filterNot(_ eq caller), current, "Undo")
    }
    val (snapshot, ctrl, msg) = result
    if snapshot.nonEmpty then snapshot.foreach(_.onUpdate(ctrl, msg))
    msg

  def redo(caller: Observer | Null = null): String =
    val result = synchronized {
      future match
        case Nil          => (Nil, current, "Nothing to redo")
        case next :: rest =>
          history = current :: history
          future  = rest
          current = next
          (observers.toList.filterNot(_ eq caller), current, "Redo")
    }
    val (snapshot, ctrl, msg) = result
    if snapshot.nonEmpty then snapshot.foreach(_.onUpdate(ctrl, msg))
    msg
