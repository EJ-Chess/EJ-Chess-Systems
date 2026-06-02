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

## Dienste starten — drei Szenarien

Wähle das passende Szenario. **Jedes Szenario ist vollständig in sich** — nicht mehrere gleichzeitig ausführen.

---

### Szenario A — Lokal Dev mit H2 (kein Docker)

Drei Terminals öffnen — **Reihenfolge ist wichtig**.

```bash
# Terminal 1 — Bot-Service zuerst starten (Port 8081)
./gradlew :modules:bot-service:quarkusDev
```

```bash
# Terminal 2 — Game-Service (Port 8080) — erst starten wenn Terminal 1 läuft
./gradlew :modules:chess-api:quarkusDev
```

```bash
# Terminal 3 — Web-UI (Port 5173)
cd modules/chess-ui
npm install        # nur einmal nötig
npm run dev
```

> **Warum diese Reihenfolge?**  
> Der Game-Service prüft beim Start ob der Bot-Service erreichbar ist (Health Check).  
> Läuft der Bot-Service nicht, antwortet der Bot in der Web-UI nie — Schwarz bleibt dauerhaft am Zug.

#### URLs (Szenario A)

| URL | Dienst |
|-----|--------|
| http://localhost:5173 | Web-UI |
| http://localhost:8080/q/swagger-ui | Swagger UI (Game-Service) |
| http://localhost:8081/q/swagger-ui | Swagger UI (Bot-Service) |
| http://localhost:8080/q/dev-ui | Quarkus Dev-UI |
| http://localhost:8082 | H2 Web-Console |

#### H2-Datenbank prüfen (Szenario A)

Unter **http://localhost:8082** einloggen:
- JDBC URL: `jdbc:h2:mem:chess`
- Benutzer: `sa`
- Passwort: *(leer lassen)*

```sql
-- Anführungszeichen erforderlich — Slick erstellt lowercase-Namen, H2 ist case-sensitiv
SELECT * FROM "games";
```

> Die Tabelle ist nach einem Service-Neustart leer — H2 in-memory ist flüchtig.  
> Für persistente Daten → Szenario C mit PostgreSQL.

---

### Szenario B — Docker mit H2 (kein PostgreSQL, kein pgAdmin)

Die Web-UI ist im Docker-Image enthalten — **kein `npm run dev` nötig**.

```bash
# Schritt 1: Quarkus-JARs bauen (einmal, oder nach Code-Änderungen)
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild
```

```bash
# Schritt 2: Alle Dienste starten
docker-compose up --build -d
```

> **Wann muss Schritt 1 wiederholt werden?**  
> Die Docker-Images kopieren die lokal gebauten JARs hinein — **nicht** automatisch bei jedem `docker-compose up`.  
> Schritt 1 ist nötig: erstes Mal, oder nach Code-Änderungen in `chess-api` / `bot-service`.  
> Nur Docker-/Config-Änderungen? → Nur Schritt 2.

#### URLs (Szenario B)

| URL | Dienst |
|-----|--------|
| http://localhost:5173 | Web-UI |
| http://localhost:8080/q/swagger-ui | API-Dokumentation (Game) |
| http://localhost:8081/q/swagger-ui | API-Dokumentation (Bot) |
| http://localhost:8080/q/health | Health Check |

> pgAdmin ist in diesem Szenario **nicht verfügbar** — dafür Szenario C verwenden.

---

### Szenario C — Docker mit PostgreSQL (persistente Daten + pgAdmin)

Die Web-UI ist im Docker-Image enthalten — **kein `npm run dev` nötig**.

```bash
# Schritt 1: Quarkus-JARs bauen (einmal, oder nach Code-Änderungen)
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild
```

```bash
# Schritt 2: Alle Dienste + PostgreSQL + pgAdmin starten
docker-compose -f docker-compose.yml -f docker-compose.db.yml --profile db up --build -d
```

> **`--profile db` ist entscheidend** — ohne dieses Flag starten PostgreSQL und pgAdmin **nicht**.  
> http://localhost:5050 ist nur mit `--profile db` erreichbar.

#### URLs (Szenario C)

| URL | Dienst |
|-----|--------|
| http://localhost:5173 | Web-UI |
| http://localhost:8080/q/swagger-ui | API-Dokumentation (Game) |
| http://localhost:8081/q/swagger-ui | API-Dokumentation (Bot) |
| http://localhost:5050 | pgAdmin (admin@chess.com / admin) |
| http://localhost:8080/q/health | Health Check |

**pgAdmin-Verbindung einrichten:**  
Host: `postgres` — Port: `5432` — DB: `chess` — User: `chess`

#### Persistenz-Nachweis

1. Web-UI → Bot-Spiel erstellen → `gameId` aus der URL notieren
2. `docker-compose restart game-service`
3. Swagger UI → `GET /games/{gameId}/state` → Spiel ist noch vorhanden ✓

---

### Szenario D — Docker mit Jaeger (Distributed Tracing)

```bash
# Schritt 1: JARs bauen
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild

# Schritt 2: Mit Tracing starten
docker-compose --profile observability up --build -d

# Oder: PostgreSQL + Tracing kombiniert
docker-compose --profile db --profile observability up --build -d
```

| URL | Dienst |
|-----|--------|
| http://localhost:16686 | Jaeger Tracing UI |

> Das Jaeger-Image wird beim ersten Start aus dem Internet geladen.

---

### Szenario E — Kubernetes (k3s) auf dem Uni-Server

Die komplette Platform läuft auf `aim-chess-2` als Kubernetes-Cluster (k3s).

```bash
# Schritt 1: JARs und Docker Images lokal bauen
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild
cd modules/chess-api && docker build -t eja-chess/game-service:latest .
cd ../bot-service && docker build -t eja-chess/bot-service:latest .
cd ../chess-ui && docker build -t eja-chess/chess-ui:latest .

# Schritt 2: Images zum Server übertragen (lokal auf eurem Rechner)
docker save eja-chess/game-service:latest eja-chess/bot-service:latest eja-chess/chess-ui:latest \
  | gzip | ssh chess@aim-chess-2 "gunzip | sudo k3s ctr images import -"

# Schritt 3: k3s auf dem Server installieren (auf dem Server!)
# Siehe: docs/readme/kubernetes.md — "Schritt 1"

# Schritt 4: Manifeste anwenden (auf dem Server!)
kubectl apply -f k8s/
```

#### URLs (Szenario E)

| URL | Dienst |
|-----|--------|
| http://141.37.74.141:30080 | Web-UI (über VPN) |
| http://141.37.74.141:30050 | pgAdmin (optional, über VPN) |

**Vollständige Anleitung:** [docs/readme/kubernetes.md](docs/readme/kubernetes.md)

> Szenario E ermöglicht mehreren Teamkollegen gleichzeitig über VPN zu spielen. Die Datenbank läuft persistent auf dem Server.

---

### Docker — Dienste stoppen & Logs

```bash
# Alle Container stoppen & entfernen (alle Profile angeben, die gestartet wurden)
docker-compose --profile db --profile observability down
```
```bash
# Logs aller Container (live)
docker-compose logs -f
```
```bash
# Logs eines einzelnen Dienstes
docker-compose logs -f game-service
docker-compose logs -f bot-service
```
```bash
# Nur Web-UI neu bauen (nach React-Änderungen, kein JAR-Build nötig)
docker-compose up --build -d chess-ui
```

---

## Desktop-Client (JavaFX) — vollständig unabhängig

Der Desktop-Client ist eine **eigenständige JavaFX-App** — kein anderer Dienst muss laufen.  
Kann jederzeit separat gestartet werden, unabhängig von Web-UI, Game-Service und Bot-Service.

```bash
./gradlew :modules:chess-bot:run
```

---

## Persistenz (Datenbank)

Der Game-Service speichert alle Partien in einer Datenbank.  
Zwei Branches implementieren dasselbe Interface mit unterschiedlichen Technologien:

| Branch | Ansatz | Datenbank (Dev/Test) | Datenbank (Prod) |
|--------|--------|----------------------|------------------|
| `feature/persistence-approach-a` | **Slick (FRM)** | H2 in-memory | PostgreSQL |
| `feature/persistence-panache-approach` | **Panache / Hibernate (JPA)** | H2 in-memory | PostgreSQL |

Detaillierte Anleitungen: [docs/readme/persistence-demo.md](docs/readme/persistence-demo.md)

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
| pgAdmin — PostgreSQL Web-UI | [docs/readme/pgadmin.md](docs/readme/pgadmin.md) |
| **Persistenz — Slick vs. Panache Vergleich** | **[docs/readme/persistence-slick-vs-panache.md](docs/readme/persistence-slick-vs-panache.md)** |
| **Streams erklärt — Präsentation (Pekko + FS2)** | **[docs/readme/STREAMS_PRESENTATION.md](docs/readme/STREAMS_PRESENTATION.md)** |
| Reactive Streams Implementation | [docs/readme/REACTIVE_STREAMS_IMPLEMENTATION.md](docs/readme/REACTIVE_STREAMS_IMPLEMENTATION.md) |
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
