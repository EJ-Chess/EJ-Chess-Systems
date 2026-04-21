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

## Docker — alle Dienste starten

### Schritt 1: Quarkus-JARs bauen

```bash
# Verzeichnis: Projektroot (EJ-Chess-Systems)
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild
```

Dauert ~1–2 Minuten. Die Quarkus fast-jars werden unter `build/quarkus-app/` gebaut.

> **Wann muss Schritt 1 ausgeführt werden?**  
> Die Docker-Images kopieren die lokal gebauten JARs hinein — **nicht** beim jedem `docker-compose up` automatisch neu.  
> Schritt 1 ist nötig, wenn:
> - **erstes Mal** (noch keine JARs vorhanden)
> - **nach Code-Änderungen** in `chess-api` oder `bot-service`
>
> Reine Docker- oder Config-Änderungen (z. B. `docker-compose.yml`, `application.properties`) brauchen nur Schritt 2.

### Schritt 2: Docker Compose starten
Verzeichnis: Projektroot (EJ-Chess-Systems)

```bash
# Ohne Jaeger (Standard — empfohlen für lokale Entwicklung)
docker-compose up --build -d
```

```bash
# Mit Jaeger / Distributed Tracing (benötigt Internetzugang beim ersten Start)
docker-compose --profile observability up --build -d
```

Die Container starten detached (`-d`). Warten Sie ~30 Sekunden bis Health Checks grün sind.

> **Hinweis zu Jaeger:** Das Jaeger-Image wird beim ersten Start aus dem Internet geladen.  
> Schlägt das fehl (kein Internetzugang, Proxy), einfach ohne `--profile observability` starten —  
> alle Kernfunktionen (Web-UI, Game-Service, Bot-Service) laufen auch ohne Tracing.

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
| http://localhost:16686 | Jaeger — Distributed Tracing UI |

### Dienste stoppen & Logs
Verzeichnis: Projektroot (EJ-Chess-Systems)

```bash
# Alle Container stoppen & entfernen
docker-compose down
```

```bash
# Logs aller Container streamen (live)
docker-compose logs -f
```

```bash
# Logs nur eines Dienstes
docker-compose logs -f game-service
docker-compose logs -f bot-service
docker-compose logs -f chess-ui
```

---

## Dienste lokal starten (Dev-Modus)

### Game-Service (Port 8080)
```bash
./gradlew :modules:chess-api:quarkusDev
```

### Bot-Service (Port 8081)
```bash
./gradlew :modules:bot-service:quarkusDev
```

### Web-UI (Port 5173)
```bash
cd modules/chess-ui
npm install
npm run dev
```

### Desktop-Client (JavaFX)
```bash
./gradlew :modules:chess-bot:run
```

> Für Bot-Spiele über die Web-UI müssen **Game-Service und Bot-Service gleichzeitig laufen**.

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
| Bot-KI        | Greedy-Random-Algorithmus, ELO-gesteuert |
| Web-Frontend  | React 18, TypeScript, Vite, Tailwind CSS |
| Desktop-GUI   | ScalaFX 21, JavaFX 21                    |
| Build         | Gradle 9, Node 20                        |
| Tests         | ScalaTest, Vitest, Rest-Assured          |

---

## Weiterführende Dokumentation

| Thema | Datei |
|-------|-------|
| Docker & Containerisierung | [docs/readme/docker.md](docs/readme/docker.md) |
| **Microservices — Code-Showcase** | **[docs/readme/microservices-showcase.md](docs/readme/microservices-showcase.md)** |
| Resilience (Health, Circuit Breaker, OpenAPI) | [docs/readme/resilience.md](docs/readme/resilience.md) |
| Microservice-Architektur (detailliert) | [docs/readme/microservice-approach-a.md](docs/readme/microservice-approach-a.md) |
| Web-UI Funktionen | [docs/readme/web-ui.md](docs/readme/web-ui.md) |
| Architektur-Entscheidungen (ADRs) | [docs/adr/](docs/adr/) |
| Offene Probleme | [docs/unresolved.md](docs/unresolved.md) |

---

## Branches

| Branch | Inhalt |
|--------|--------|
| `main` | Stabiler Stand |
| `feature/microservice-approach-a` | Microservice-Aufteilung (game-service + bot-service) |
| `feature/web-ui-approach-a` | Moderne Web-UI (React/TypeScript) |
| `feature/bot-implementation` | Bot-Implementierung + En-Passant-Fix |
