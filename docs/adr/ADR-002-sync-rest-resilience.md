# ADR-002 — Synchrones REST mit MicroProfile Fault Tolerance

**Status:** Accepted  
**Datum:** 2026-04-20  
**Autor:** EJa Chess Team

---

## Kontext

Nach der Microservice-Aufteilung (ADR-001) ruft der Game-Service den Bot-Service
synchron per HTTP auf (`POST /bot/move`). Damit entsteht eine Laufzeitabhängigkeit:

- Wenn der Bot-Service langsam oder nicht erreichbar ist, blockiert der Game-Service.
- Ohne Absicherung können wiederholte Fehler zu einem Kaskadenausfall führen.

Außerdem ist die API zwischen den Diensten undokumentiert und nur durch Code einsehbar.

---

## Entscheidung

Wir setzen **synchrones REST** mit **MicroProfile Fault Tolerance** ein und ergänzen
die Dienste um **Health Checks** und **automatisch generierte OpenAPI-Dokumentation**.

### 1. Synchrones REST (kein Messaging)

**Begründung:**
- Bot-Anfragen sind Request-Response: der Client wartet auf den Zug.
- Asynchrones Messaging (Kafka, RabbitMQ) würde erhebliche Infrastruktur erfordern
  und den Ablauf komplizieren (Korrelations-IDs, Timeout-Handling).
- Für ein Schach-MVP mit einzelnen Zügen ist synchrones HTTP ausreichend und verständlicher.

### 2. MicroProfile Fault Tolerance (`quarkus-smallrye-fault-tolerance`)

`BotClient.fetchMove` ist mit drei Annotations abgesichert:

```
@CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.75, delay = 10000L)
@Timeout(3000L)
@Fallback(fallbackMethod = "fetchMoveFallback")
```

- **`@Timeout`** — nach 3 Sekunden wird der Call abgebrochen.
- **`@CircuitBreaker`** — öffnet nach ≥ 75 % Fehlern in 4 Calls; kein weiterer
  HTTP-Call für 10 Sekunden (verhindert Kaskadenausfall).
- **`@Fallback`** — gibt `None` zurück; der Game-Service überspringt den Bot-Zug
  und bleibt spielbar.

Alle drei Mechanismen werden durch CDI-Interceptoren bereitgestellt — kein
manueller Try-Catch-Boilerplate, kein eigener Thread-Pool.

### 3. Health Checks (`quarkus-smallrye-health`)

Beide Dienste exponieren `/q/health/ready`.
docker-compose verwendet diese Endpunkte als `depends_on: condition: service_healthy`,
sodass der Game-Service erst startet, wenn der Bot-Service bereit ist.

### 4. OpenAPI / Swagger UI (`quarkus-smallrye-openapi`)

Quarkus generiert die API-Dokumentation automatisch aus den JAX-RS-Annotations.
Endpunkte: `/q/openapi` (YAML) und `/q/swagger-ui` (interaktive UI).

---

## Konsequenzen

### Positiv

- **Keine zusätzliche Infrastruktur** — kein Broker, kein extra Deployment.
- **Kein Kaskadenausfall** — Circuit Breaker isoliert Bot-Service-Fehler.
- **Immer spielbar** — Bot-Ausfall überspringt nur den Bot-Zug, blockiert nicht.
- **Lebende Dokumentation** — OpenAPI bleibt automatisch mit dem Code synchron.
- **Skalierbarkeit** — Bot-Service kann horizontal skaliert werden; der Game-Service
  braucht nur die URL zu kennen.

### Negativ / Einschränkungen

- **Kopplung zur Laufzeit** — Ausfall des Bot-Service wird vom Game-Service bemerkt
  (auch wenn er graceful reagiert).
- **Latenz** — synchroner HTTP-Call fügt ~1–50 ms Netzwerklatenz pro Bot-Zug hinzu.
- **Circuit-Breaker-Zustand ist instanzlokal** — bei mehreren Game-Service-Instanzen
  öffnet der Circuit pro Instanz unabhängig (für MVP akzeptabel).

---

## Erwogene Alternativen

| Alternative | Warum verworfen |
|-------------|-----------------|
| Asynchrones Messaging (Kafka) | Infrastruktur-Overhead zu groß für ein MVP; Korrelation der Antworten komplex |
| Direkte Gradle-Abhängigkeit (Monolith) | Verliert unabhängige Deploybarkeit und Skalierbarkeit |
| gRPC statt REST | Kein signifikanter Vorteil bei einem einzigen Endpunkt; REST ist hier ausreichend |
| Reactive REST Client (Quarkus REST Client) | Würde reaktive Programmierung im gesamten Game-Service erfordern; unverhältnismäßig |

---

## Weiterführend

- [ADR-001 — Microservice-Architektur](ADR-001-microservice-architecture.md)
- [docs/readme/resilience.md](../readme/resilience.md) — Implementierungsdetails
- [docs/readme/docker.md](../readme/docker.md) — Docker-Setup mit Health-Check-Abhängigkeiten
