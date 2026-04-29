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

```bash
./gradlew :modules:chess-api:quarkusDev
```

- Web-UI: **http://localhost:5173** (separates Terminal: `cd modules/chess-ui && npm run dev`)
- Swagger UI: **http://localhost:8080/q/swagger-ui**
- H2 Web-Console: **http://localhost:8082**
  - JDBC URL: `jdbc:h2:mem:chess`
  - Benutzer: `sa` / Passwort: leer lassen

### Spiel erstellen und Daten in H2 prüfen

1. Swagger UI → `POST /games` → Execute
2. `gameId` aus der Antwort kopieren
3. H2 Console → `SELECT * FROM games;` → Zeile erscheint
4. Service neustarten → `SELECT * FROM games;` → Tabelle leer (in-memory wird verworfen)

---

## 4. Mit Docker + Web-UI starten (H2 in-memory)

```bash
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild
docker-compose up --build
```

- Web-UI: **http://localhost:5173**
- Swagger: **http://localhost:8080/q/swagger-ui**

---

## 5. Mit PostgreSQL starten (persistente Daten)

```bash
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild

QUARKUS_DATASOURCE_DB_KIND=postgresql \
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/chess \
QUARKUS_DATASOURCE_USERNAME=chess \
QUARKUS_DATASOURCE_PASSWORD=chess \
docker-compose --profile db up --build
```

- pgAdmin: **http://localhost:5050** (admin@chess.local / admin)
- Verbindung in pgAdmin: Host `postgres`, Port `5432`, DB `chess`, User `chess`

### Persistenz-Nachweis

1. Swagger UI → `POST /games` → `gameId` notieren
2. `docker-compose restart game-service`
3. Swagger UI → `GET /games/{gameId}` → Spiel ist noch da ✓

---

## 6. Was wurde implementiert?

| Datei | Beschreibung |
|-------|-------------|
| `persistence/Tables.scala` | Slick `TableQuery[GamesTable]` + alle DBIO-Actions |
| `persistence/DatabaseConfig.scala` | Wraps Quarkus Agroal DataSource; erkennt H2/Postgres automatisch |
| `persistence/GameRepository.scala` | CRUD via `Await.result(db.run(...))` |
| `service/GameService.scala` | Speichert/lädt Spiele; rekonstruiert `GameManager` aus PGN |
| `test/.../GameRepositorySpec.scala` | 6 ScalaTest-Tests gegen echte H2-DB |

---

## Vergleich mit Panache → [persistence-slick-vs-panache.md](persistence-slick-vs-panache.md)
