package de.eljachess.bot

import de.eljachess.chess.model.Color
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GameFactorySpec extends AnyFlatSpec with Matchers:

  "GameFactory" should "create game with human opponent and no bot" in {
    val setup      = GameSetup("Alice", Color.White, HumanOpponent)
    val controller = GameFactory.createGame(setup)
    controller.bot shouldBe None
  }

  it should "create game with bot opponent and correct ELO" in {
    val setup      = GameSetup("Bob", Color.Black, BotOpponent(EloLevel.Intermediate))
    val controller = GameFactory.createGame(setup)
    controller.bot shouldBe defined
    controller.bot.get.elo shouldBe 1400
  }

  it should "store player color in controller" in {
    val setup      = GameSetup("Charlie", Color.Black, HumanOpponent)
    val controller = GameFactory.createGame(setup)
    controller.playerColor shouldBe Some(Color.Black)
  }

  it should "always start with White's turn" in {
    val setup      = GameSetup("David", Color.Black, HumanOpponent)
    val controller = GameFactory.createGame(setup)
    controller.currentTurn shouldBe Color.White
  }

  it should "create bot with custom ELO" in {
    val setup      = GameSetup("Eve", Color.White, BotOpponent(EloLevel.custom(1200)))
    val controller = GameFactory.createGame(setup)
    controller.bot shouldBe defined
    controller.bot.get.elo shouldBe 1200
  }

  it should "auto-play bot move when bot is White and player is Black" in {
    // Player is Black, bot is White → after creating game, bot (White) should auto-play
    // because it's White's turn at game start and bot's color != player's color
    val setup      = GameSetup("Frank", Color.Black, BotOpponent(EloLevel.Advanced))
    val controller = GameFactory.createGame(setup)
    // After bot auto-plays, it should be Black's turn (human's turn)
    controller.currentTurn shouldBe Color.Black
  }
