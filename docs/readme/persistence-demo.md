# Persistenz-Demo — Slick (feature/persistence-approach-a)

Dieser Branch implementiert Persistenz mit **Slick 3.5 (FRM)** und H2 (Dev) / PostgreSQL (Prod).

---

## 1. Branch wechseln

```bash
git checkout feature/persistence-approach-a
```

---

## 2. Tests ausführen (kein Docker nötig)

```bash
./gradlew :modules:chess-api:test
```

Der `GameRepositorySpec` läuft gegen eine echte H2-In-Memory-Datenbank — kein Mock, kein Quarkus-Container:

```
GameRepositorySpec > GameRepository should insert and find a game by id  PASSED
GameRepositorySpec > GameRepository should return None for an unknown game id  PASSED
GameRepositorySpec > GameRepository should update pgn correctly  PASSED
GameRepositorySpec > GameRepository should delete a game  PASSED
GameRepositorySpec > GameRepository should store bot config fields  PASSED
GameRepositorySpec > GameRepository should findAll returns all inserted games  PASSED
```

---

## 3. Dev-Modus starten (H2 in-memory)

Drei Terminals öffnen — **Reihenfolge beachten**:

```bash
# Terminal 1 — Bot-Service (Port 8081) — zuerst starten!
./gradlew :modules:bot-service:quarkusDev
```

```bash
# Terminal 2 — Game-Service (Port 8080 + H2-Console Port 8082)
./gradlew :modules:chess-api:quarkusDev
```

```bash
# Terminal 3 — Web-UI (Port 5173)
cd modules/chess-ui && npm run dev
```

> **Ohne Terminal 1 (bot-service)** antwortet der Bot in der Web-UI nie —  
> Schwarz bleibt dauerhaft am Zug.

### Verfügbare URLs

| URL | Dienst |
|-----|--------|
| http://localhost:5173 | Web-UI |
| http://localhost:8080/q/swagger-ui | Swagger UI |
| http://localhost:8082 | H2 Web-Console |

### H2 Web-Console

Unter **http://localhost:8082** einloggen:
- JDBC URL: `jdbc:h2:mem:chess`
- Benutzer: `sa`
- Passwort: *(leer lassen)*

Tabelle abfragen — **Anführungszeichen sind Pflicht** (Slick erstellt lowercase-Namen, H2 ist case-sensitiv):

```sql
SELECT * FROM "games";
```

### Spiel erstellen und Daten in H2 prüfen

1. Web-UI oder Swagger UI → Spiel erstellen
2. H2 Console → `SELECT * FROM "games";` → Zeile erscheint ✓
3. Game-Service neustarten → Tabelle leer (H2 in-memory wird verworfen — erwartet)

---

## 4. Mit Docker + Web-UI starten (H2 in-memory)

```bash
# Schritt 1: JARs bauen (einmal, oder nach Code-Änderungen)
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild

# Schritt 2: Alle Dienste starten
docker-compose up --build -d
```

- Web-UI: **http://localhost:5173**
- Swagger: **http://localhost:8080/q/swagger-ui**

---

## 5. Mit PostgreSQL starten (persistente Daten)

```bash
# Schritt 1: JARs bauen
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild

# Schritt 2: Alle Dienste + PostgreSQL + pgAdmin starten
docker-compose --profile db up --build -d
```

- pgAdmin: **http://localhost:5050** (admin@chess.local / admin)
- Verbindung in pgAdmin: Host `postgres`, Port `5432`, DB `chess`, User `chess`

### Persistenz-Nachweis

1. Web-UI → Bot-Spiel erstellen → `gameId` aus der URL notieren
2. `docker-compose restart game-service`
3. Swagger UI → `GET /games/{gameId}/state` → Spiel ist noch da ✓

---

## 6. Was wurde implementiert?

| Datei | Beschreibung |
|-------|-------------|
| `persistence/Tables.scala` | Slick `TableQuery[GamesTable]` + alle DBIO-Actions |
| `persistence/DatabaseConfig.scala` | Wraps Quarkus Agroal DataSource; erkennt H2/Postgres automatisch |
| `persistence/GameRepository.scala` | CRUD via `Await.result(db.run(...))` |
| `service/GameService.scala` | Speichert/lädt Spiele; rekonstruiert `GameManager` aus PGN beim Laden |
| `startup/H2ConsoleStarter.scala` | Startet H2 Web-Console auf Port 8082 im Dev-Modus |
| `test/.../GameRepositorySpec.scala` | 6 ScalaTest-Tests gegen echte H2-DB |

---

## Vergleich mit Panache → [persistence-slick-vs-panache.md](persistence-slick-vs-panache.md)
