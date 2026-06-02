# Tournament Service

A Quarkus microservice implementing Swiss-system chess tournaments for the EJa Chess platform.

## Overview

The Tournament Service manages the complete lifecycle of chess bot tournaments:
- **Tournament Creation**: Directors create tournaments with configurable rounds, time controls, and rating flags
- **Player Management**: Bots join/withdraw from tournaments in "created" state
- **Round Execution**: Automatic Swiss pairing algorithm with rematch avoidance
- **Real-time Streaming**: NDJSON event stream for live tournament updates
- **Results Tracking**: Standings, pairings, and Buchholz tiebreaker scoring

## Architecture

```
Tournament Service (Port 8086)
├── REST Controllers (JAX-RS)
│   ├── TournamentResource: CRUD operations
│   ├── ParticipationResource: Join/withdraw
│   ├── ResultsResource: Standings & exports
│   └── StreamResource: NDJSON event stream
├── Business Logic
│   ├── TournamentService: Orchestration
│   ├── SwissService: Pairing algorithm
│   └── TournamentStreamService: Event bus
├── Persistence (Slick ORM)
│   ├── DatabaseConfig: H2/PostgreSQL detection
│   ├── Tables: Schema definition
│   └── TournamentRepository: Data access
└── Health Checks
    └── GameServiceHealthCheck: Dependency monitoring
```

## API Endpoints

### Tournament Management

```bash
# Create tournament (director only)
POST /api/tournament
Authorization: Bearer <director_id>
Content-Type: application/json
{
  "name": "Swiss Open 2026",
  "nbRounds": 5,
  "clockLimit": 300,
  "clockIncrement": 3,
  "rated": true
}
→ 201 Created: Tournament object

# List all tournaments (grouped by status)
GET /api/tournament
→ 200 OK: { "created": [...], "started": [...], "finished": [...] }

# Get tournament details
GET /api/tournament/{id}
→ 200 OK: Tournament object with standings

# Delete tournament (director only)
DELETE /api/tournament/{id}
Authorization: Bearer <director_id>
→ 204 No Content

# Start tournament (director only, min 2 bots)
POST /api/tournament/{id}/start
Authorization: Bearer <director_id>
→ 200 OK: Tournament object (status="started")
```

### Player Participation

```bash
# Join tournament
POST /api/tournament/{id}/join
Authorization: Bearer <bot_id>
→ 200 OK

# Withdraw from tournament (created state only)
POST /api/tournament/{id}/withdraw
Authorization: Bearer <bot_id>
→ 200 OK
```

### Results & Streaming

```bash
# Get standings (NDJSON)
GET /api/tournament/{id}/results?nb=10
→ 200 OK: NDJSON stream of Result objects

# Get round pairings
GET /api/tournament/{id}/round/{round_number}
→ 200 OK: { "round": N, "pairings": [...] }

# Stream tournament events (long-poll)
GET /api/tournament/{id}/stream
Authorization: Bearer <bot_id>
→ 200 OK: NDJSON stream
   {"type":"tournamentStarted","timestamp":"..."}
   {"type":"roundStarted","round":1,"timestamp":"..."}
   {"type":"gameStart","round":1,"gameId":"g1","color":"white","timestamp":"..."}
   ...
```

## Swiss Algorithm

The SwissService implements the standard Swiss pairing system:

1. **Sorting**: Players sorted by (descending points, descending Buchholz tiebreak)
2. **Pairing**: Top half paired with bottom half
3. **Rematch Avoidance**: If pairing would repeat, slide to next available opponent
4. **Color Assignment**: Player with lower color balance gets white
5. **Bye Handling**: With odd player count, last-ranked player gets a bye

**Tiebreaker (Buchholz Score)**: Sum of all opponents' current points.

### Example

```
Round 1: 4 bots (bot1, bot2, bot3, bot4)
→ bot1 vs bot3 (white)
→ bot2 vs bot4 (white)

Round 2 (after bot1=1pt, bot2=1pt, bot3=0.5pt, bot4=0.5pt):
Sorted: [bot1(1.0), bot2(1.0), bot3(0.5), bot4(0.5)]
→ bot1 vs bot3 (rematch! slide to bot4)
→ bot1 vs bot4 (white) ✓
→ bot2 vs bot3 (white)
```

## Configuration

### `application.properties`

```properties
# Service
quarkus.application.name=tournament-service
quarkus.http.port=8086

# Database (auto-detected from JDBC URL)
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:tournament;DB_CLOSE_DELAY=-1
quarkus.datasource.username=sa
quarkus.datasource.password=

# Environment-specific overrides
%prod.quarkus.datasource.db-kind=postgresql
%prod.quarkus.datasource.jdbc.url=jdbc:postgresql://db:5432/chess_tournaments
%prod.quarkus.datasource.username=${DB_USER}
%prod.quarkus.datasource.password=${DB_PASSWORD}

# Observability
quarkus.otel.exporter.otlp.endpoint=http://localhost:4317
quarkus.micrometer.export.prometheus.enabled=true
```

## Database Schema

```sql
CREATE TABLE tournaments (
  id              VARCHAR(36)  PRIMARY KEY,
  name            VARCHAR(255) NOT NULL,
  status          VARCHAR(10)  NOT NULL DEFAULT 'created',
  nb_rounds       INTEGER      NOT NULL,
  current_round   INTEGER      NOT NULL DEFAULT 0,
  clock_limit     INTEGER      NOT NULL DEFAULT 300,
  clock_increment INTEGER      NOT NULL DEFAULT 3,
  rated           BOOLEAN      NOT NULL DEFAULT TRUE,
  created_by      VARCHAR(100) NOT NULL,
  starts_at       VARCHAR(30)
);

CREATE TABLE tournament_players (
  tournament_id VARCHAR(36)  NOT NULL,
  bot_id        VARCHAR(100) NOT NULL,
  bot_name      VARCHAR(100) NOT NULL,
  points        DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  tie_break     DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  wins          INTEGER NOT NULL DEFAULT 0,
  draws         INTEGER NOT NULL DEFAULT 0,
  losses        INTEGER NOT NULL DEFAULT 0,
  nb_games      INTEGER NOT NULL DEFAULT 0,
  color_balance INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (tournament_id, bot_id)
);

CREATE TABLE pairings (
  id            VARCHAR(36)  PRIMARY KEY,
  tournament_id VARCHAR(36)  NOT NULL,
  round         INTEGER      NOT NULL,
  white_id      VARCHAR(100) NOT NULL,
  white_name    VARCHAR(100) NOT NULL,
  black_id      VARCHAR(100) NOT NULL,
  black_name    VARCHAR(100) NOT NULL,
  game_id       VARCHAR(36),
  winner        VARCHAR(5)
);
```

## Building & Testing

### Build

```bash
# Compile and run tests
./gradlew :modules:tournament-service:build

# Run only SwissService unit tests (pairing algorithm)
./gradlew :modules:tournament-service:test --tests "*SwissServiceSpec*"

# Full build with JAR creation
./gradlew :modules:tournament-service:assemble
```

### Running Locally

```bash
# Start in dev mode (port 8086)
./gradlew :modules:tournament-service:quarkusDev

# Query the service
curl -X GET http://localhost:8086/api/tournament

# Create a tournament
curl -X POST http://localhost:8086/api/tournament \
  -H "Authorization: Bearer director1" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Tournament",
    "nbRounds": 3,
    "clockLimit": 300,
    "clockIncrement": 3,
    "rated": true
  }'

# Start streaming events
curl -H "Authorization: Bearer bot1" \
  http://localhost:8086/api/tournament/{tournament_id}/stream
```

## Authentication

JWT Bearer tokens (RFC 7519):
- **Bearer token format**: `Authorization: Bearer <JWT>`
- **Token subject (`sub` claim)**: User ID (director or bot)
- **Issuer (`iss` claim)**: `https://nowchess.local`

**Example JWT Payload:**
```json
{
  "sub": "bot1",
  "iss": "https://nowchess.local",
  "iat": 1717338000,
  "exp": 1717341600
}
```

For development, JwtHandler extracts the `sub` claim from the token payload (base64url-decoded). In production, use a proper JWT library with public key validation and full signature verification.

**Authorization Rules:**
- **Director**: Can create tournaments (`POST /api/tournament`) and start tournaments (`POST /api/tournament/{id}/start`)
- **Bot**: Can join tournaments (`POST /api/tournament/{id}/join`), withdraw, and stream events (`GET /api/tournament/{id}/stream`)

## Known Issues & Limitations

See `docs/unresolved.md` for details on:
- TournamentService/TournamentRepository unit test setup (requires @QuarkusTest)
- Integration test infrastructure needs work

## Integration with Chess API

The Tournament Service creates games via the Chess API service:

```scala
// In TournamentService.startRound()
private def createGame(whiteId: String, blackId: String): Option[String] =
  val req = HttpRequest.newBuilder()
    .uri(URI.create("http://localhost:8080/games"))
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString("""{"playerColor":"white"}"""))
    .build()
  // Parse gameId from response...
```

**Health Check**: `GET http://localhost:8086/q/health/readiness` probes Chess API availability.

## Development Notes

### Tech Stack
- **Framework**: Quarkus 3.8.x (reactive REST)
- **Language**: Scala 3.4.x
- **ORM**: Slick 3.5.x
- **Database**: H2 (dev) / PostgreSQL (prod)
- **Testing**: ScalaTest (AnyFlatSpec) + REST Assured

### Project Structure
```
src/
├── main/scala/de/eljachess/tournament/
│   ├── config/           # Jackson configuration
│   ├── controller/       # REST endpoints
│   ├── dto/             # Request/response objects
│   ├── health/          # Health checks
│   ├── persistence/     # Database layer
│   └── service/         # Business logic
└── test/scala/...       # Unit & integration tests
```

### Adding New Endpoints

1. Create a controller in `controller/`
2. Inject `TournamentService` via `@Inject`
3. Use `extractBotId()` helper for token parsing
4. Use `handleError()` for error responses

```scala
@Path("/api/tournament")
class YourResource:
  @Inject var service: TournamentService = uninitialized

  @GET @Path("{id}/custom")
  def yourEndpoint(@PathParam("id") id: String): Response =
    service.findTournament(id) match
      case None => Response.status(404).entity(Error("not found")).build()
      case Some(t) => Response.ok(t).build()
```

## License

Part of the EJa Chess bachelor project (HTWG Konstanz, Summer 2026).
