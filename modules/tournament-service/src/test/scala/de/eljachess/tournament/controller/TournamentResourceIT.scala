package de.eljachess.tournament.controller

import de.eljachess.tournament.dto.{Tournament, Ok}
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.junit.jupiter.api.{BeforeEach, Test}

@QuarkusTest
class TournamentResourceIT:

  @BeforeEach
  def setup(): Unit =
    RestAssured.baseURI = "http://localhost"
    RestAssured.port = 8086

  @Test
  def testCreateTournament(): Unit =
    val response = RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body("""{"name":"Test Tourney","nbRounds":3,"clockLimit":300,"clockIncrement":3,"rated":true}""")
      .header("Authorization", "Bearer director1")
      .`when`()
      .post("/api/tournament")
      .`then`()
      .statusCode(201)
      .extract()
      .`as`(classOf[Tournament])

    assert(response.fullName == "Test Tourney", s"Expected name 'Test Tourney', got '${response.fullName}'")
    assert(response.status == "created", s"Expected status 'created', got '${response.status}'")

  @Test
  def testListTournaments(): Unit =
    RestAssured
      .`given`()
      .`when`()
      .get("/api/tournament")
      .`then`()
      .statusCode(200)

  @Test
  def testCreateTournamentWithoutAuth(): Unit =
    RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body("""{"name":"Test","nbRounds":3,"clockLimit":300,"clockIncrement":3}""")
      .`when`()
      .post("/api/tournament")
      .`then`()
      .statusCode(401)

  @Test
  def testJoinTournament(): Unit =
    // Create tournament
    val createResp = RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body("""{"name":"Test","nbRounds":3,"clockLimit":300,"clockIncrement":3}""")
      .header("Authorization", "Bearer director1")
      .`when`()
      .post("/api/tournament")
      .`then`()
      .statusCode(201)
      .extract()
      .`as`(classOf[Tournament])

    // Join tournament
    RestAssured
      .`given`()
      .header("Authorization", "Bearer bot1")
      .`when`()
      .post(s"/api/tournament/${createResp.id}/join")
      .`then`()
      .statusCode(200)

  @Test
  def testStartTournamentFailsWithOneBot(): Unit =
    // Create tournament
    val createResp = RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body("""{"name":"Test","nbRounds":3,"clockLimit":300,"clockIncrement":3}""")
      .header("Authorization", "Bearer director1")
      .`when`()
      .post("/api/tournament")
      .`then`()
      .statusCode(201)
      .extract()
      .`as`(classOf[Tournament])

    // Join with only 1 bot
    RestAssured
      .`given`()
      .header("Authorization", "Bearer bot1")
      .`when`()
      .post(s"/api/tournament/${createResp.id}/join")
      .`then`()
      .statusCode(200)

    // Try to start — should fail with 409
    RestAssured
      .`given`()
      .header("Authorization", "Bearer director1")
      .`when`()
      .post(s"/api/tournament/${createResp.id}/start")
      .`then`()
      .statusCode(409)

  @Test
  def testStartTournamentSucceedsWithTwoBots(): Unit =
    // Create tournament
    val createResp = RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body("""{"name":"Test","nbRounds":3,"clockLimit":300,"clockIncrement":3}""")
      .header("Authorization", "Bearer director1")
      .`when`()
      .post("/api/tournament")
      .`then`()
      .statusCode(201)
      .extract()
      .`as`(classOf[Tournament])

    // Join with 2 bots
    RestAssured
      .`given`()
      .header("Authorization", "Bearer bot1")
      .`when`()
      .post(s"/api/tournament/${createResp.id}/join")
      .`then`()
      .statusCode(200)

    RestAssured
      .`given`()
      .header("Authorization", "Bearer bot2")
      .`when`()
      .post(s"/api/tournament/${createResp.id}/join")
      .`then`()
      .statusCode(200)

    // Start tournament — should succeed
    val startResp = RestAssured
      .`given`()
      .header("Authorization", "Bearer director1")
      .`when`()
      .post(s"/api/tournament/${createResp.id}/start")
      .`then`()
      .statusCode(200)
      .extract()
      .`as`(classOf[Tournament])

    assert(startResp.status == "started", s"Expected status 'started', got '${startResp.status}'")
