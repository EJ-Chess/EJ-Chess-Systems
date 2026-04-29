# EJa Chess Systems

Schach-Plattform im Rahmen des Moduls **Software Architektur (SS26)** an der HTWG Konstanz.  
Entwickelt als verteiltes System bestehend aus mehreren unabhängig deployten Diensten.

---

## Modulstruktur

```
.
├── core/                  Reine Schach-Engine (Board, Fen, Pgn, GameController …)
├── modules/
│   ├── chess-api/         Game-Service — REST-API, Session-Verwaltung       Port 8080
│   ├── bot-service/       Bot-Service  — KI-Zugberechnung (stateless)        Port 8081
│   ├── chess-ui/          Web-UI       — React/TypeScript SPA                Port 5173
│   └── chess-bot/         Desktop-Client — JavaFX GUI + TUI (standalone)
├── docs/
│   ├── adr/               Architecture Decision Records
│   ├── readme/            Themen-spezifische Dokumentationen
│   └── unresolved.md      Bekannte offene Probleme
├── build.gradle.kts       Root-Build (gemeinsame Versionen)
└── settings.gradle.kts    Modul-Deklarationen
```

---

## Dienste lokal starten (Dev-Modus)

### Web-UI + Bot-Spiele (3 Terminals)

> Reihenfolge beachten — Bot-Service muss vor Game-Service laufen.

```bash
# Terminal 1 — Bot-Service (Port 8081)
./gradlew :modules:bot-service:quarkusDev
```

```bash
# Terminal 2 — Game-Service (Port 8080)
./gradlew :modules:chess-api:quarkusDev
```

```bash
# Terminal 3 — Web-UI (Port 5173)
cd modules/chess-ui
npm install        # nur einmal nötig
npm run dev
```

### Dev-URLs

| URL | Dienst |
|-----|--------|
| http://localhost:5173 | Web-UI |
| http://localhost:8080/q/swagger-ui | Swagger UI (Game-Service) |
| http://localhost:8081/q/swagger-ui | Swagger UI (Bot-Service) |
| http://localhost:8080/q/dev-ui | Quarkus Dev-UI |
| http://localhost:8082 | H2 Web-Console (nur Dev-Modus) |

---

### Desktop-Client (JavaFX) — unabhängig, separat

Der Desktop-Client ist eine **eigenständige JavaFX-App** — völlig unabhängig von Web-UI,
Game-Service und Bot-Service. Kein anderer Dienst muss dafür laufen.

```bash
./gradlew :modules:chess-bot:run
```

---

## Persistenz (Datenbank)

Der Game-Service speichert alle Partien in einer Datenbank.  
Zwei Branches implementieren dasselbe Interface mit unterschiedlichen Technologien:

| Branch | Ansatz | Datenbank (Dev) | Datenbank (Prod) |
|--------|--------|-----------------|------------------|
| `feature/persistence-approach-a` | **Slick (FRM)** | H2 in-memory | PostgreSQL |
| `feature/persistence-panache-approach` | **Panache / Hibernate (JPA)** | H2 in-memory | PostgreSQL |

### H2-Datenbank im Dev-Modus prüfen

Nach `./gradlew :modules:chess-api:quarkusDev` ist die H2 Web-Console verfügbar:

**http://localhost:8082**
- JDBC URL: `jdbc:h2:mem:chess`
- Benutzer: `sa`
- Passwort: *(leer lassen)*

Tabelle abfragen (Anführungszeichen erforderlich, da Slick lowercase-Namen erstellt):
```sql
SELECT * FROM "games";
```

> Die Tabelle ist leer nach einem Service-Neustart — H2 in-memory ist flüchtig.  
> Für persistente Daten → PostgreSQL mit `--profile db` (siehe unten).

### Mit PostgreSQL starten (persistente Daten)

```bash
# Schritt 1: JARs bauen
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild

# Schritt 2: Alle Dienste + PostgreSQL starten
docker-compose --profile db up --build -d
```

Nach dem Neustart des game-service die Partien über `GET /games/{gameId}` abrufen —  
sie sind noch vorhanden. pgAdmin: **http://localhost:5050** (admin@chess.local / admin)

---

## Docker — alle Dienste starten

### Schritt 1: Quarkus-JARs bauen

```bash
# Verzeichnis: Projektroot (EJ-Chess-Systems)
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild
```

Dauert ~1–2 Minuten. Die Quarkus fast-jars werden unter `build/quarkus-app/` gebaut.

> **Wann muss Schritt 1 ausgeführt werden?**  
> Die Docker-Images kopieren die lokal gebauten JARs hinein — **nicht** automatisch bei jedem `docker-compose up`.  
> Schritt 1 ist nötig wenn:
> - **erstes Mal** (noch keine JARs vorhanden)
> - **nach Code-Änderungen** in `chess-api` oder `bot-service`
>
> Reine Docker- oder Config-Änderungen (z. B. `docker-compose.yml`) brauchen nur Schritt 2.

### Schritt 2: Docker Compose starten

```bash
# Standard (H2 in-memory, ohne Jaeger)
docker-compose up --build -d
```

```bash
# Mit PostgreSQL (persistente Daten)
docker-compose --profile db up --build -d
```

```bash
# Mit Jaeger / Distributed Tracing
docker-compose --profile observability up --build -d
```

```bash
# Mit allem
docker-compose --profile db --profile observability up --build -d
```

```bash
# Nur Web-UI neu bauen (nach React-Änderungen)
docker-compose up --build -d chess-ui
```

Die Container starten detached (`-d`). Ca. 30 Sekunden bis Health Checks grün sind.

> **Hinweis zu Jaeger:** Das Jaeger-Image wird beim ersten Start aus dem Internet geladen.  
> Ohne Internetzugang einfach ohne `--profile observability` starten.

### Verfügbare URLs

| URL | Dienst |
|-----|--------|
| http://localhost:5173 | Web-UI (React SPA) |
| http://localhost:8080 | Game-Service REST API |
| http://localhost:8081 | Bot-Service REST API |
| http://localhost:8080/q/swagger-ui | API-Dokumentation (Game) |
| http://localhost:8081/q/swagger-ui | API-Dokumentation (Bot) |
| http://localhost:8080/q/health | Health Check (Game, inkl. bot-service) |
| http://localhost:8081/q/health | Health Check (Bot) |
| http://localhost:8080/q/metrics | Prometheus-Metriken (Game) |
| http://localhost:8081/q/metrics | Prometheus-Metriken (Bot) |
| http://localhost:16686 | Jaeger — Distributed Tracing UI (nur `--profile observability`) |
| http://localhost:5050 | pgAdmin — PostgreSQL UI (nur `--profile db`) |

### Dienste stoppen & Logs

```bash
# Alle Container stoppen & entfernen
docker-compose --profile observability --profile db down
```

```bash
# Logs aller Container streamen (live)
docker-compose logs -f
```

```bash
# Logs nur eines Dienstes
docker-compose logs -f game-service
docker-compose logs -f bot-service
```

---

## Tests

```bash
# Alle Module
./gradlew test

# Einzelnes Modul
./gradlew :modules:chess-api:test
./gradlew :modules:bot-service:test
./gradlew :core:test
./gradlew :modules:chess-bot:test

# Web-UI
./gradlew :modules:chess-ui:npmTest
```

---

## Tech-Stack

| Schicht       | Technologie                              |
|---------------|------------------------------------------|
| Backend       | Scala 3.5, Quarkus 3.25, Jakarta REST    |
| Persistenz    | Slick 3.5 (FRM) / Panache+Hibernate (JPA) |
| Bot-KI        | Greedy-Random-Algorithmus, ELO-gesteuert |
| Web-Frontend  | React 18, TypeScript, Vite, Tailwind CSS |
| Desktop-GUI   | ScalaFX 21, JavaFX 21                    |
| Build         | Gradle 9, Node 20                        |
| Tests         | ScalaTest, Vitest, Rest-Assured          |
| Observability | OpenTelemetry, Jaeger, Micrometer/Prometheus |

---

## Weiterführende Dokumentation

| Thema | Datei |
|-------|-------|
| Docker & Containerisierung | [docs/readme/docker.md](docs/readme/docker.md) |
| **Microservices — Abgabe-Übersicht** | **[docs/readme/microservices.md](docs/readme/microservices.md)** |
| Microservices — Code-Showcase | [docs/readme/microservices-showcase.md](docs/readme/microservices-showcase.md) |
| Resilience (Health, Circuit Breaker, OpenAPI) | [docs/readme/resilience.md](docs/readme/resilience.md) |
| Microservice-Architektur (detailliert) | [docs/readme/microservice-approach-a.md](docs/readme/microservice-approach-a.md) |
| Web-UI Funktionen | [docs/readme/web-ui.md](docs/readme/web-ui.md) |
| **Persistenz — Demo & Anleitung** | **[docs/readme/persistence-demo.md](docs/readme/persistence-demo.md)** |
| **Persistenz — Slick vs. Panache Vergleich** | **[docs/readme/persistence-slick-vs-panache.md](docs/readme/persistence-slick-vs-panache.md)** |
| Architektur-Entscheidungen (ADRs) | [docs/adr/](docs/adr/) |
| Offene Probleme | [docs/unresolved.md](docs/unresolved.md) |

---

## Branches

| Branch | Inhalt |
|--------|--------|
| `main` | Stabiler Stand |
| `feature/microservice-approach-a` | Microservice-Aufteilung (game-service + bot-service) |
| `feature/web-ui-approach-a` | Moderne Web-UI (React/TypeScript) |
| `feature/bot-implementation` | Bot-Implementierung |
| `feature/persistence-approach-a` | Persistenz mit **Slick (FRM)** + H2/PostgreSQL |
| `feature/persistence-panache-approach` | Persistenz mit **Panache/Hibernate (JPA)** + H2/PostgreSQL |
