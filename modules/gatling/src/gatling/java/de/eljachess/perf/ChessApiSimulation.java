package de.eljachess.perf;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling load test for chess-api.
 *
 * Thresholds:
 *   - p95 response time  < 200 ms  (global)
 *   - error rate         < 1%      (global)
 *
 * Run:  ./gradlew :modules:gatling:gatlingRun
 *       -DbaseUrl=http://localhost:8080   (default)
 */
public class ChessApiSimulation extends Simulation {

    private static final String BASE_URL =
            System.getProperty("baseUrl", "http://localhost:8080");

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json")
            .shareConnections();

    // ── Scenario ──────────────────────────────────────────────────────────────

    private final ScenarioBuilder gameLifecycle = scenario("Chess Game Lifecycle")

            .exec(http("POST /games  (create)")
                    .post("/games")
                    .body(StringBody("{}")).asJson()
                    .check(status().is(201))
                    .check(jsonPath("$.gameId").saveAs("gameId")))

            .pause(Duration.ofMillis(200))

            .exec(http("POST /games/:id/moves  (e2→e4)")
                    .post("/games/#{gameId}/moves")
                    .body(StringBody("{\"from\":\"e2\",\"to\":\"e4\"}")).asJson()
                    .check(status().is(200)))

            .pause(Duration.ofMillis(200))

            .exec(http("GET /games/:id  (state)")
                    .get("/games/#{gameId}")
                    .check(status().is(200))
                    .check(jsonPath("$.fen").exists()))

            .exec(http("GET /games/:id/moves  (legal moves)")
                    .get("/games/#{gameId}/moves")
                    .check(status().is(200))
                    .check(jsonPath("$.count").ofInt().gt(0)))

            .exec(http("POST /games/:id/undo")
                    .post("/games/#{gameId}/undo")
                    .body(StringBody("")).asJson()
                    .check(status().is(200)))

            .exec(http("DELETE /games/:id  (cleanup)")
                    .delete("/games/#{gameId}")
                    .check(status().is(204)));

    // ── Load profile ──────────────────────────────────────────────────────────

    {
        setUp(
                gameLifecycle.injectOpen(
                        rampUsers(5).during(Duration.ofSeconds(10)),
                        constantUsersPerSec(5).during(Duration.ofSeconds(30)),
                        rampUsers(0).during(Duration.ofSeconds(10))
                )
        )
        .assertions(
                // p95 latency < 200 ms for all requests
                global().responseTime().percentile(95).lte(200),
                // fewer than 1% failures
                global().failedRequests().percent().lte(1.0)
        )
        .protocols(httpProtocol);
    }
}
