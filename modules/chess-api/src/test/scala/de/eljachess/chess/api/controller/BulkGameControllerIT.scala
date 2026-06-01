package de.eljachess.chess.api.controller

import de.eljachess.chess.api.dto.BulkGameResult
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.junit.jupiter.api.Test

@QuarkusTest
class BulkGameControllerIT:

  @Test
  def testBulkGameWithCount3(): Unit =
    val result: BulkGameResult = RestAssured.`given`()
      .contentType(ContentType.JSON)
      .body("""{"count":3}""")
      .`when`()
      .post("/games/bulk")
      .`then`()
      .statusCode(200)
      .extract()
      .`as`(classOf[BulkGameResult])

    assert(result.total == 3, s"Expected total=3, got ${result.total}")
    assert(result.successful == 3, s"Expected successful=3, got ${result.successful}")
    assert(result.failed == 0, s"Expected failed=0, got ${result.failed}")

  @Test
  def testBulkGameWithCount0(): Unit =
    val result: BulkGameResult = RestAssured.`given`()
      .contentType(ContentType.JSON)
      .body("""{"count":0}""")
      .`when`()
      .post("/games/bulk")
      .`then`()
      .statusCode(200)
      .extract()
      .`as`(classOf[BulkGameResult])

    assert(result.total == 0, s"Expected total=0, got ${result.total}")
    assert(result.successful == 0, s"Expected successful=0, got ${result.successful}")
    assert(result.failed == 0, s"Expected failed=0, got ${result.failed}")
