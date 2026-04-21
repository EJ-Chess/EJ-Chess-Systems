package de.eljachess.chess.api.health

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.eclipse.microprofile.health.HealthCheckResponse

class BotServiceHealthCheckSpec extends AnyFlatSpec with Matchers:

  // Subclass that overrides targetUrl to a guaranteed-unreachable address,
  // so tests are environment-independent (bot-service may or may not be running).
  private class IsolatedCheck extends BotServiceHealthCheck:
    override protected def targetUrl: String = "http://localhost:1"

  "BotServiceHealthCheck" should "return DOWN when bot-service is not reachable" in {
    val check = new IsolatedCheck()
    val response = check.call()
    response.getStatus shouldBe HealthCheckResponse.Status.DOWN
  }

  it should "name the check 'bot-service'" in {
    val check = new IsolatedCheck()
    val response = check.call()
    response.getName shouldBe "bot-service"
  }

  it should "include error data in the DOWN response" in {
    val check = new IsolatedCheck()
    val response = check.call()
    val data = response.getData
    data.isPresent shouldBe true
  }
