package de.eljachess.connector.dto

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ModelsSpec extends AnyFlatSpec with Matchers:

  private val mapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  // ── RegisterResponse ───────────────────────────────────────────────────────

  "RegisterResponse" should "deserialize from JSON" in:
    val json = """{"id":"bot-1","token":"jwt-abc"}"""
    val r = mapper.readValue(json, classOf[RegisterResponse])
    r.id shouldBe "bot-1"
    r.token shouldBe "jwt-abc"

  it should "ignore unknown fields" in:
    val json = """{"id":"x","token":"y","extra":"ignored"}"""
    val r = mapper.readValue(json, classOf[RegisterResponse])
    r.id shouldBe "x"

  // ── TournamentSummary ──────────────────────────────────────────────────────

  "TournamentSummary" should "deserialize with optional fullName" in:
    val json = """{"id":"t1","status":"created","fullName":"Open Tournament"}"""
    val t = mapper.readValue(json, classOf[TournamentSummary])
    t.id shouldBe "t1"
    t.status shouldBe "created"
    t.fullName shouldBe Some("Open Tournament")

  it should "deserialize without fullName" in:
    val json = """{"id":"t2","status":"started"}"""
    val t = mapper.readValue(json, classOf[TournamentSummary])
    t.fullName shouldBe None

  // ── TournamentStreamEvent ──────────────────────────────────────────────────

  "TournamentStreamEvent" should "deserialize a gameStart event" in:
    val json = """{"gameStart":{"round":1,"gameId":"g-42","color":"white"}}"""
    val e = mapper.readValue(json, classOf[TournamentStreamEvent])
    e.gameStart shouldBe defined
    e.gameStart.get.gameId shouldBe "g-42"
    e.gameStart.get.color shouldBe "white"
    e.gameStart.get.round shouldBe 1
    e.tournamentStarted shouldBe None

  it should "deserialize a tournamentStarted event" in:
    val json = """{"tournamentStarted":true}"""
    val e = mapper.readValue(json, classOf[TournamentStreamEvent])
    e.tournamentStarted shouldBe Some(true)
    e.gameStart shouldBe None

  it should "deserialize a roundStarted event" in:
    val json = """{"roundStarted":{"round":2}}"""
    val e = mapper.readValue(json, classOf[TournamentStreamEvent])
    e.roundStarted.map(_.round) shouldBe Some(2)

  it should "deserialize a roundFinished event" in:
    val json = """{"roundFinished":{"round":1}}"""
    val e = mapper.readValue(json, classOf[TournamentStreamEvent])
    e.roundFinished.map(_.round) shouldBe Some(1)

  it should "deserialize a tournamentFinished event with winner" in:
    val json = """{"tournamentFinished":{"id":"bot-1","name":"Alpha"}}"""
    val e = mapper.readValue(json, classOf[TournamentStreamEvent])
    e.tournamentFinished.flatMap(_.name) shouldBe Some("Alpha")

  // ── GameStreamEvent ────────────────────────────────────────────────────────

  "GameStreamEvent" should "deserialize a gameState event" in:
    val json =
      """{"gameState":{"fen":"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
        |"turn":"black","status":"ongoing","moves":["e2e4"]}}""".stripMargin
    val e = mapper.readValue(json, classOf[GameStreamEvent])
    e.gameState shouldBe defined
    e.gameState.get.turn shouldBe "black"
    e.gameState.get.status shouldBe "ongoing"
    e.gameState.get.fen should include("KQkq")

  it should "deserialize a move event" in:
    val json = """{"move":{"uci":"e7e5","fen":"some-fen","turn":"white"}}"""
    val e = mapper.readValue(json, classOf[GameStreamEvent])
    e.move shouldBe defined
    e.move.get.uci shouldBe "e7e5"
    e.move.get.turn shouldBe "white"

  it should "deserialize a gameEnd event with winner" in:
    val json = """{"gameEnd":{"winner":"white","status":"checkmate"}}"""
    val e = mapper.readValue(json, classOf[GameStreamEvent])
    e.gameEnd shouldBe defined
    e.gameEnd.get.winner shouldBe Some("white")
    e.gameEnd.get.status shouldBe "checkmate"

  it should "deserialize a draw gameEnd event" in:
    val json = """{"gameEnd":{"winner":null,"status":"stalemate"}}"""
    val e = mapper.readValue(json, classOf[GameStreamEvent])
    e.gameEnd.get.winner shouldBe None

  // ── BotMoveResponse / UCI conversion ──────────────────────────────────────

  "BotMoveResponse" should "convert from+to to UCI" in:
    BotMoveResponse("e2", "e4").toUci shouldBe "e2e4"
    BotMoveResponse("d7", "d5").toUci shouldBe "d7d5"
    BotMoveResponse("e7", "e8").toUci shouldBe "e7e8"

  it should "deserialize from JSON and produce correct UCI" in:
    val json = """{"from":"g1","to":"f3"}"""
    val r = mapper.readValue(json, classOf[BotMoveResponse])
    r.toUci shouldBe "g1f3"
