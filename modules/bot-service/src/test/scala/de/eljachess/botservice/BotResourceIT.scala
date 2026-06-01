package de.eljachess.botservice

import de.eljachess.botservice.dto.{BotMoveRequest, BotMoveResponse}
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach

@QuarkusTest
class BotResourceIT:

  private val initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
  private val blackFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"

  @BeforeEach
  def setup(): Unit =
    RestAssured.baseURI = "http://localhost"
    RestAssured.port = 8081

  @Test
  def testPostBotMoveWithValidRequestReturns200: Unit =
    val request = BotMoveRequest(fen = initialFen, color = "white", elo = 1400)

    RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body(request)
      .`when`()
      .post("/bot/move")
      .`then`()
      .statusCode(200)

  @Test
  def testPostBotMoveResponseHasFromAndToFields: Unit =
    val request = BotMoveRequest(fen = initialFen, color = "white", elo = 1400)

    RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body(request)
      .`when`()
      .post("/bot/move")
      .`then`()
      .statusCode(200)
      .extract()
      .jsonPath()
      .getObject(".", classOf[BotMoveResponse]) // verifies JSON deserialization

  @Test
  def testPostBotMoveWithBlackReturns200: Unit =
    val request = BotMoveRequest(fen = blackFen, color = "black", elo = 1800)

    RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body(request)
      .`when`()
      .post("/bot/move")
      .`then`()
      .statusCode(200)

  @Test
  def testPostBotMoveWithInvalidFenReturns503: Unit =
    val request = BotMoveRequest(fen = "not-a-valid-fen", color = "white", elo = 1400)

    RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body(request)
      .`when`()
      .post("/bot/move")
      .`then`()
      .statusCode(503)
