package de.eljachess.chess.controller

import scala.collection.mutable

class GameManager(initial: GameController):
  private var current   = initial
  private var history   = List.empty[(GameController, ParsedMove)]
  private var future    = List.empty[(GameController, ParsedMove)]
  private val observers = mutable.Buffer.empty[Observer]

  def addObserver(o: Observer): Unit = synchronized {
    if !observers.contains(o) then observers += o
  }
  def state: GameController           = synchronized { current }

  def move(input: String, caller: Observer | Null = null): String =
    val (snapshot, ctrl, msg) = synchronized {
      val parsed = CommandParser.parse(input)
      val (next, msg) = current.handleCommand(input)
      if next != current then
        parsed match
          case Right(parsedMove) =>
            history = (current, parsedMove) :: history
            future  = Nil
            current = next
            (observers.toList.filterNot(_ eq caller), current, msg)
          case Left(_) =>
            (Nil, current, msg)
      else
        (Nil, current, msg)
    }
    snapshot.foreach(_.onUpdate(ctrl, msg))
    msg

  def undo(caller: Observer | Null = null): String =
    val result = synchronized {
      history match
        case Nil =>
          (Nil, current, "Nothing to undo")
        case (prev, move) :: rest =>
          future  = (current, move) :: future
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
        case Nil =>
          (Nil, current, "Nothing to redo")
        case (next, move) :: rest =>
          history = (current, move) :: history
          future  = rest
          current = next
          (observers.toList.filterNot(_ eq caller), current, "Redo")
    }
    val (snapshot, ctrl, msg) = result
    if snapshot.nonEmpty then snapshot.foreach(_.onUpdate(ctrl, msg))
    msg
