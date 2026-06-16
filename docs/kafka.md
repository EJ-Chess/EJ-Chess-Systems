# Kafka Integration — EJa Chess Systems

## Was wurde implementiert

Die Kommunikation zwischen **chess-api** (Game Service) und **bot-service** wurde von einem synchronen HTTP-Call auf einen **asynchronen Kafka-Kanal** umgestellt.

Wenn ein Spieler einen Zug macht und der Bot an der Reihe ist, veröffentlicht chess-api ein Event auf einem Kafka-Topic. bot-service verarbeitet dieses Event über einen Pekko-Stream und schickt das Ergebnis zurück. chess-api konsumiert die Antwort und wendet den Bot-Zug auf das Spiel an.

---

## Architektur

```
Spieler-Zug (REST)
     │
     ▼
chess-api: GameService.applyBotMoveIfNeeded()
     │
     │  publiziert BotMoveRequestEvent
     ▼
Kafka Topic: chess.move-requests
     │
     ▼
bot-service: KafkaBotConsumer (Pekko Stream)
     │  ┌─────────────────────────────────────────┐
     │  │  Kafka Poller Thread                    │
     │  │    → Source.queue (Pekko, 1000 Slots)   │
     │  │      → mapAsync(8): BotEngine.bestMove  │
     │  │        → KafkaProducer.send             │
     │  └─────────────────────────────────────────┘
     │  produziert BotResponseEvent
     ▼
Kafka Topic: chess.bot-responses
     │
     ▼
chess-api: BotMoveKafkaConsumer (Poller Thread)
     │
     ▼
chess-api: GameService.applyBotMoveAsync()
```

**Vorher (HTTP):** chess-api blockierte bis bot-service antwortete (~synchron, Timeout nach 3 s)

**Nachher (Kafka):** chess-api returned sofort; Bot-Zug kommt asynchron an

---

## Warum diese Architektur sinnvoll ist

### Entkopplung
chess-api und bot-service müssen nicht gleichzeitig laufen. Kafka puffert Events — wenn bot-service kurz nicht erreichbar ist, werden Anfragen nicht verworfen, sondern warten im Topic.

### Pekko-Stream-Integration
bot-service nutzt bereits Pekko-Streams (`BotStreamProcessor`). Der neue `KafkaBotConsumer` erweitert dieses Muster: ein dedizierter Poller-Thread füttert Records in eine `Source.queue`; `mapAsync(parallelism=8)` verarbeitet mehrere Bot-Anfragen parallel — identisch zum bestehenden HTTP-Stream-Ansatz, aber jetzt über den Event-Bus.

### Skalierbarkeit
Mehrere Instanzen von bot-service können parallel in derselben Consumer Group (`bot-service-group`) laufen und die Partitionen von `chess.move-requests` aufteilen, ohne dass chess-api geändert werden muss.

---

## Kafka Topics

| Topic                  | Producer   | Consumer   | Payload (JSON)                                      |
|------------------------|------------|------------|-----------------------------------------------------|
| `chess.move-requests`  | chess-api  | bot-service | `{gameId, fen, color, elo}`                        |
| `chess.bot-responses`  | bot-service| chess-api  | `{gameId, from, to}`                               |

Key ist jeweils die `gameId` → alle Events einer Partie landen in derselben Partition → Reihenfolge garantiert.

---

## Implementierte Dateien

### Neue Klassen

| Datei | Modul | Beschreibung |
|-------|-------|--------------|
| `modules/bot-service/src/main/scala/de/eljachess/botservice/KafkaBotConsumer.scala` | bot-service | Pekko Stream: Kafka Consumer → BotEngine → Kafka Producer |
| `modules/chess-api/src/main/scala/de/eljachess/chess/api/kafka/BotMoveKafkaProducer.scala` | chess-api | Kafka Producer für Bot-Anfragen |
| `modules/chess-api/src/main/scala/de/eljachess/chess/api/kafka/BotMoveKafkaConsumer.scala` | chess-api | Kafka Consumer für Bot-Antworten |

### Geänderte Klassen

| Datei | Änderung |
|-------|----------|
| `modules/chess-api/src/main/scala/de/eljachess/chess/api/service/GameService.scala` | `applyBotMoveIfNeeded` publiziert jetzt zu Kafka statt HTTP; neues `applyBotMoveAsync` für den Kafka-Consumer |

### Tests

| Datei | Was wird getestet |
|-------|-------------------|
| `modules/bot-service/src/test/scala/de/eljachess/botservice/KafkaBotConsumerSpec.scala` | `processMoveEvent` und `processRecord` ohne Kafka-Broker (stub Producer) |
| `modules/chess-api/src/test/scala/de/eljachess/chess/api/kafka/BotMoveKafkaProducerSpec.scala` | JSON-Serialisierung von `BotMoveRequestEvent` / `BotResponseEvent` |

### Konfiguration & Infrastruktur

| Datei | Beschreibung |
|-------|--------------|
| `docker-compose-kafka.yml` | Single-node Zookeeper + Kafka für lokale Entwicklung |
| `modules/bot-service/src/main/resources/application.properties` | `kafka.bootstrap.servers`, `kafka.enabled` |
| `modules/chess-api/src/main/resources/application.properties` | `kafka.bootstrap.servers`, `kafka.enabled` |
| `modules/bot-service/build.gradle.kts` | Kafka-Clients-Dependency hinzugefügt |
| `modules/chess-api/build.gradle.kts` | Kafka-Clients-Dependency hinzugefügt |

---

## Lokale Entwicklung

### Kafka starten

```bash
docker-compose -f docker-compose-kafka.yml up -d
```

### Services starten (mit Kafka)

```bash
# Bot-Service
KAFKA_ENABLED=true ./gradlew :modules:bot-service:quarkusDev

# Chess-API
KAFKA_ENABLED=true ./gradlew :modules:chess-api:quarkusDev
```

### Oder mit docker-compose (alles zusammen)

```bash
docker-compose -f docker-compose-kafka.yml -f docker-compose.yml up --build
```

`KAFKA_BOOTSTRAP_SERVERS=kafka:9092` in den Service-Containern setzen (bereits vorbereitet).

---

## Konfiguration

Beide Services lesen folgende Umgebungsvariablen:

| Variable | Default | Beschreibung |
|----------|---------|--------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka-Broker-Adresse |
| `KAFKA_ENABLED` | `true` | `false` deaktiviert Kafka (Tests, Non-Kafka-Deployments) |

In Tests ist Kafka deaktiviert — die bestehenden Unit-Tests laufen ohne Broker weiter.

---

## Vorteile von Kafka gegenüber direktem HTTP

**1. Ausfallsicher / entkoppelt**
Wenn bot-service down ist, schlägt nichts fehl. chess-api publiziert ins Topic und kehrt sofort zurück. bot-service liest die Records wenn es wieder oben ist — kein verlorener Zug, kein HTTP 503.

**2. Records bleiben erhalten**
Anders als ein HTTP-Call ist ein Kafka-Record kein fire-and-forget. Er bleibt im Topic gespeichert (konfigurierbare Retention) bis er verarbeitet wurde. Bei einem Neustart von bot-service werden ausstehende Anfragen einfach nachgeholt.

**3. Skalierung ohne Codeänderung**
Eine zweite bot-service Instanz kann jederzeit gestartet werden. Beide lesen aus demselben Topic (`group.id = bot-service-group`), Kafka verteilt die Records automatisch auf beide — ohne dass chess-api davon weiß.
