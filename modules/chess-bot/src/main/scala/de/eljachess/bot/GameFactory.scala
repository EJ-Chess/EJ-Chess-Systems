package de.eljachess.bot

import de.eljachess.chess.model.{Board, Color}
import de.eljachess.chess.controller.GameController

object GameFactory:
  def createGame(setup: GameSetup): GameController =
    val bot = setup.opponent match
      case HumanOpponent    => None
      case BotOpponent(elo) => Some(GreedyRandomBot(elo))

    val initial = GameController(
      board       = Board.initial,
      currentTurn = Color.White,
      bot         = bot,
      playerColor = Some(setup.playerColor)
    )

    // If bot is White (player chose Black), bot plays first move immediately
    bot match
      case Some(b) if setup.playerColor == Color.Black =>
        b.nextMove(initial.board, Color.White) match
          case None => initial
          case Some((from, to)) =>
            initial.board.move(from, to) match
              case None           => initial
              case Some(newBoard) =>
                GameController(newBoard, Color.Black, 0, 1, bot, Some(Color.Black))
      case _ => initial
