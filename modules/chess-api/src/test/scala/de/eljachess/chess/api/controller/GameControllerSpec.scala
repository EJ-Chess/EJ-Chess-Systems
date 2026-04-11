package de.eljachess.chess.api.controller

import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Test
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.hamcrest.Matchers.*

@QuarkusTest
class GameControllerSpec:

  @Test
  def testCreateGame(): Unit =
    RestAssured.`given`()
      .contentType(ContentType.JSON)
      .body("{}")
      .when()
      .post("/games")
      .`then`()
      .statusCode(201)
      .body("gameId", notNullValue())
      .body("fen", notNullValue())

  @Test
  def testGetGameNotFound(): Unit =
    RestAssured.`given`()
      .when()
      .get("/games/nonexistent-id")
      .`then`()
      .statusCode(404)
      .body("error", notNullValue())

  @Test
  def testDeleteNotFound(): Unit =
    RestAssured.`given`()
      .when()
      .delete("/games/nonexistent-id")
      .`then`()
      .statusCode(404)

  @Test
  def testMakeMoveOnNewGame(): Unit =
    val gameId = RestAssured.`given`()
      .contentType(ContentType.JSON)
      .body("{}")
      .when()
      .post("/games")
      .`then`()
      .statusCode(201)
      .extract()
      .path[String]("gameId")

    RestAssured.`given`()
      .contentType(ContentType.JSON)
      .body("""{"from":"e2","to":"e4"}""")
      .when()
      .post(s"/games/$gameId/moves")
      .`then`()
      .statusCode(200)
      .body("success", equalTo(true))

  @Test
  def testMakeMoveInvalidMove(): Unit =
    val gameId = RestAssured.`given`()
      .contentType(ContentType.JSON)
      .body("{}")
      .when()
      .post("/games")
      .`then`()
      .statusCode(201)
      .extract()
      .path[String]("gameId")

    RestAssured.`given`()
      .contentType(ContentType.JSON)
      .body("""{"from":"e2","to":"e5"}""")
      .when()
      .post(s"/games/$gameId/moves")
      .`then`()
      .statusCode(400)
      .body("success", equalTo(false))

  @Test
  def testGetLegalMoves(): Unit =
    val gameId = RestAssured.`given`()
      .contentType(ContentType.JSON)
      .body("{}")
      .when()
      .post("/games")
      .`then`()
      .statusCode(201)
      .extract()
      .path[String]("gameId")

    RestAssured.`given`()
      .when()
      .get(s"/games/$gameId/moves")
      .`then`()
      .statusCode(200)
      .body("count", equalTo(20))

  @Test
  def testGetFen(): Unit =
    val gameId = RestAssured.`given`()
      .contentType(ContentType.JSON)
      .body("{}")
      .when()
      .post("/games")
      .`then`()
      .statusCode(201)
      .extract()
      .path[String]("gameId")

    RestAssured.`given`()
      .when()
      .get(s"/games/$gameId/fen")
      .`then`()
      .statusCode(200)
      .body("fen", notNullValue())

  @Test
  def testImportFen(): Unit =
    val gameId = RestAssured.`given`()
      .contentType(ContentType.JSON)
      .body("{}")
      .when()
      .post("/games")
      .`then`()
      .statusCode(201)
      .extract()
      .path[String]("gameId")

    RestAssured.`given`()
      .contentType(ContentType.JSON)
      .body("""{"fen":"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}""")
      .when()
      .post(s"/games/$gameId/import")
      .`then`()
      .statusCode(200)
      .body("success", equalTo(true))

  @Test
  def testDeleteGame(): Unit =
    val gameId = RestAssured.`given`()
      .contentType(ContentType.JSON)
      .body("{}")
      .when()
      .post("/games")
      .`then`()
      .statusCode(201)
      .extract()
      .path[String]("gameId")

    RestAssured.`given`()
      .when()
      .delete(s"/games/$gameId")
      .`then`()
      .statusCode(204)
