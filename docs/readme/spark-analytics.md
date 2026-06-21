# Spark Analytics – Aggregation von Schachdaten

Das Modul **`spark-analytics`** implementiert die Vorlesungsaufgabe zu Apache Spark.  
Es aggregiert Schachdaten aus zwei Quellen mit drei Varianten:

1. **File Analytics (eigene Daten)** – liest eine CSV-Datei mit eigenen Spieldaten und berechnet Statistiken
2. **Lichess Big Data** – liest echte PGN-Exporte von [database.lichess.org](https://database.lichess.org) und analysiert hunderttausende Partien
3. **Kafka Streaming** – liest `chess.move-requests` als Echtzeit-Stream und aggregiert laufend

---

## Überblick

| Aspekt | Details |
|--------|---------|
| **Sprache** | Scala **2.13** (Spark unterstützt noch kein Scala 3) |
| **Framework** | Apache Spark 3.5.3 (Structured Streaming) |
| **Kafka-Topic** | `chess.move-requests` (bereits vom chess-api produziert) |
| **Einstiegspunkte** | `ChessFileAnalytics`, `LichessFileAnalytics`, `ChessKafkaStream` |
| **Tests** | 23 Unit-Tests mit ScalaTest + JUnit 4 Runner |

---

## Architektur

```
chess_games.csv       ──►  ChessFileAnalytics  ──┐
                                                  │
lichess_*.pgn         ──►  LichessFileAnalytics   ├──►  ChessAnalytics
  (database.lichess.org)    └── LichessDataLoader ┘     (gemeinsame Aggregations-Logik)
                                 └── PGN-Parser          │
                                                   ┌─────┴──────────┐
                                                   ▼                ▼
                                           victoriesPerPlayer   winsPerColor
                                           avgBotEloBeatByPlayer  bestPlayer

chess.move-requests (Kafka)
         │
         ▼
  ChessKafkaStream
    └── Structured Streaming
        └── groupBy(gameId, color) → count / avg(elo)
        └── Console-Output (complete mode)
```

---

## Erstellte Dateien

### `modules/spark-analytics/build.gradle.kts`

Gradle-Build-Datei für das Modul.

**Warum:** Das Modul ist kein Quarkus-Service, sondern eine eigenständige Spark-Anwendung. Deshalb kein `io.quarkus`-Plugin, sondern das native `scala`-Plugin kombiniert mit `application`.

Wichtige Abschnitte:

| Zeilen | Inhalt | Grund |
|--------|--------|-------|
| 1–3 | `plugins { scala; application }` | Scala-Kompilierung + `./gradlew run` |
| 13–14 | `SCALA_VERSION = "2.13.16"` | Spark 3.5.x benötigt Scala 2.x |
| 17–18 | `scala { scalaVersion }` | Teilt Gradle die Scala-Version mit |
| 24–26 | `spark-sql_2.13:3.5.3` | Kern-Spark inkl. SparkContext und DataFrame API |
| 29–30 | `spark-sql-kafka-0-10_2.13:3.5.3` | Kafka-Source-Connector für Structured Streaming |
| 33–36 | `scalatest_2.13` + `junit-4-13_2.13` | Test-Runner (JUnit 4, nicht 5 – siehe Nachteile) |
| 39–52 | `sparkJvmArgs` (gemeinsame JVM-Optionen) | `--add-opens` für Java 17+, Hadoop-Home-Pfad |
| 54–57 | Task `run` | Startet `ChessFileAnalytics` |
| 59–68 | Task `runKafkaStream` | Startet `ChessKafkaStream` mit Kafka-Env-Var |
| 71–83 | Task `test` mit `useJUnit()` | JUnit 4 wegen Spark-Thread-Konflikt (s. Nachteile) |

---

### `src/main/scala/de/eljachess/spark/ChessAnalytics.scala`

Gemeinsames Objekt mit allen Aggregations-Funktionen. Wird von `ChessFileAnalytics`, dem Kafka-Stream und den Tests verwendet.

| Zeilen | Methode | Was sie macht |
|--------|---------|---------------|
| 22–26 | `createLocalSpark` | Erstellt eine lokale SparkSession (`local[*]`, UI deaktiviert) |
| 33–36 | `victoriesPerPlayer` | Filtert Zeilen mit `playerColor == winner`, gruppiert nach Spielername, zählt Siege absteigend |
| 41–45 | `winsPerColor` | Filtert Draws heraus, zählt Siege pro Gewinner-Farbe (white/black) |
| 50–54 | `avgBotEloBeatByPlayer` | Nur gewonnene Spiele, berechnet durchschnittliches Bot-ELO pro Spieler |
| 59–63 | `bestPlayer` | Gibt den Spieler mit den meisten Siegen zurück; „No games played" bei leerem Ergebnis |

**Warum als separates Objekt?**  
Die Aggregations-Logik ist unabhängig von der Datenquelle (Datei oder Stream). Durch die Trennung können dieselben Funktionen in Tests mit In-Memory-DataFrames getestet werden, ohne eine echte Datei oder Kafka-Verbindung zu brauchen.

---

### `src/main/scala/de/eljachess/spark/ChessFileAnalytics.scala`

Einstiegspunkt für **Schritt 1** der Aufgabe (Datei lesen).

| Zeilen | Abschnitt | Was passiert |
|--------|-----------|-------------|
| 19 | `createLocalSpark` | SparkSession im Local-Mode |
| 21–24 | CSV-Pfad | Nimmt optionalen CLI-Argument oder sucht `chess_games.csv` im Classpath via `toURI → Paths.get` (URL-Dekodierung wegen Leerzeichen im Pfad) |
| 27–31 | `spark.read.csv` | Liest CSV mit Header-Inferenz und Schema-Erkennung, legt Ergebnis in Cache |
| 33–44 | Aggregationen | Ruft alle vier `ChessAnalytics`-Methoden auf und gibt Ergebnisse aus |
| 46 | `spark.stop()` | Beendet Spark sauber |

**Beispielausgabe:**
```
── Victories per Player ─────────────
+----------+---------+
|playerName|victories|
+----------+---------+
|Charlie   |4        |
|Alice     |3        |
|Bob       |2        |
|Diana     |1        |
+----------+---------+

── Highscore / Best Player: Charlie
```

---

### `src/main/scala/de/eljachess/spark/ChessKafkaStream.scala`

Einstiegspunkt für **Schritt 2** der Aufgabe (Kafka-Stream).

| Zeilen | Abschnitt | Was passiert |
|--------|-----------|-------------|
| 22–27 | `MoveRequestSchema` | JSON-Schema für `chess.move-requests`-Nachrichten (`gameId`, `fen`, `color`, `elo`) |
| 33–38 | SparkSession | Local-Mode, UI deaktiviert |
| 41–46 | `readStream.format("kafka")` | Structured Streaming liest ab dem neuesten Offset |
| 49–53 | `from_json` | Parst die JSON-Payload aus dem Kafka-`value`-Feld |
| 56–62 | `groupBy.agg` | Zählt Zuganfragen pro Spiel (`gameId`) und Farbe (`color`), berechnet Durchschnitts-ELO |
| 65–74 | `writeStream.console` | Gibt jede Micro-Batch-Aggregation im `complete`-Modus auf der Konsole aus |
| 74 | `awaitTermination()` | Blockiert bis Ctrl+C |

**Ausgabe-Schema:**
```
+--------+-----+-------------+-------+
|gameId  |color|move_requests|avg_elo|
+--------+-----+-------------+-------+
|abc-123 |white|5            |1500.0 |
|abc-123 |black|4            |1500.0 |
+--------+-----+-------------+-------+
```

**Outputmode `complete`:** Jede neue Micro-Batch-Ausgabe zeigt den vollständigen, akkumulierten Zählerstand — nicht nur die Änderungen der letzten Sekunde.

---

### `src/main/resources/chess_games.csv`

15 Beispielspiele mit den Feldern:

| Feld | Beschreibung |
|------|-------------|
| `gameId` | Eindeutige Spiel-ID |
| `playerName` | Name des menschlichen Spielers |
| `playerColor` | Farbe des Spielers (`white` / `black`) |
| `winner` | Gewinner (`white` / `black` / `draw`) |
| `botElo` | ELO-Rating des Gegner-Bots |
| `moveCount` | Gesamtzahl der Züge |

Ein Spieler **gewinnt**, wenn `playerColor == winner`. Zeilen mit `draw` werden bei Sieg-Zählungen ignoriert.

**Warum eine separate CSV und keine DB-Anbindung?**  
Die Aufgabe verlangt explizit „*As a first step, read your data from a simple file.*". Die CSV dient als einfache, self-contained Datenquelle für lokale Demos ohne laufende Datenbank.

---

### `src/main/resources/log4j2.properties` & `src/test/resources/log4j2.properties`

Unterdrückt Sparks umfangreiche INFO-Logs (Spark 3.3+ nutzt Log4j2). Ohne diese Datei würden hunderte Zeilen Spark-Interna die eigentliche Ausgabe überlagern.

```properties
rootLogger.level = WARN
```

---

### `src/test/scala/de/eljachess/spark/ChessAnalyticsSpec.scala`

10 Unit-Tests mit AnyFlatSpec + ScalaTest Matchers.

| Zeilen | Test-Gruppe | Was getestet wird |
|--------|-------------|-------------------|
| 71–83 | `victoriesPerPlayer` | Korrekte Siege-Zählung, kein Eintrag für Verlierer |
| 85–90 | `victoriesPerPlayer` | Leeres Ergebnis bei nur Draws |
| 92–101 | `victoriesPerPlayer` | Absteigende Sortierung |
| 103–111 | `winsPerColor` | Korrekte Farb-Zählung, Draws ausgeschlossen |
| 113–117 | `winsPerColor` | Leeres Ergebnis bei nur Draws |
| 119–127 | `avgBotEloBeatByPlayer` | Korrekte Durchschnitts-ELO-Berechnung |
| 129–132 | `avgBotEloBeatByPlayer` | Leeres Ergebnis ohne Siege |
| 134–138 | `bestPlayer` | Gibt Spielernamen mit meisten Siegen zurück |
| 140–143 | `bestPlayer` | `"No games played"` bei leerem Dataset |
| 145–148 | `bestPlayer` | `"No games played"` bei nur Draws |

**Technische Besonderheit — stabiler Identifier für Spark Implicits:**

```scala
private def testDf(): DataFrame = withSpark { s =>
  import s.implicits._   // s ist ein val → stabiler Identifier ✓
  Seq(...).toDF(...)
}
```

`spark` ist ein `var` (wegen `beforeAll`-Zuweisung). Scala 2.x erlaubt `import x.implicits._` nur bei stabilen Identifiern (`val` / `object`). Die `withSpark`-Methode nimmt `spark` als `val`-Parameter entgegen, was das Import-Problem löst.

---

---

## Lichess Big Data Integration

### Datenquelle

Die Lichess Open Database ([database.lichess.org](https://database.lichess.org)) stellt monatliche PGN-Exporte aller bewerteten Partien unter der **CC0-Lizenz** bereit.

| Datei | Komprimiert | Partien |
|-------|------------|---------|
| `lichess_db_standard_rated_2013-01.pgn.zst` | 17,8 MB | 121.332 |
| Aktuelle Monate | ~30 GB | ~90 Mio. |

### PGN-Format (Lichess)

https://database.lichess.org/

```plantuml
./gradlew :modules:spark-analytics:runLichess --args="C:\Users\brand\Downloads\lichess.pgn"                                                      
```

```pgn
[Event "Rated Blitz game"]
[Site "https://lichess.org/PpwPOZMq"]
[White "Abbot"]
[Black "Costello"]
[Result "0-1"]
[WhiteElo "2100"]
[BlackElo "2000"]

1. e4 { [%eval 0.17] [%clk 0:00:30] } 1... c5 { [%eval 0.19] [%clk 0:00:30] } 0-1
```

### Mapping PGN → DataFrame

`LichessDataLoader` erzeugt pro Partie **2 Zeilen** (je eine für Weiß und Schwarz), damit `victoriesPerPlayer` und `avgBotEloBeatByPlayer` für alle Spieler korrekt funktionieren:

| PGN-Feld | DataFrame-Spalte | Beispiel |
|----------|-----------------|---------|
| `Site` (letztes Segment) | `gameId` | `PpwPOZMq` |
| `White` / `Black` | `playerName` | `Abbot` |
| `"white"` / `"black"` | `playerColor` | `white` |
| `Result` (1-0 / 0-1 / ½-½) | `winner` | `black` |
| Gegner-`WhiteElo` / `BlackElo` | `botElo` | `2000` |
| Anzahl Halbzüge (Plies) | `moveCount` | `26` |

**Move-Counting:** Kommentare `{ [%eval] [%clk] }`, Zugnummern `1.` / `1...` und NAGs `$1` werden entfernt; die verbleibenden Tokens sind die echten Züge.

### Nachgewiesener Lauf — Januar 2013

Ausgeführt am 17.06.2026 auf `lichess_db_standard_rated_2013-01.pgn` (88,5 MB entpackt):

```
══════════════════════════════════════════
  Chess Game Analytics  (Lichess PGN)
  File: lichess_db_standard_rated_2013-01.pgn
══════════════════════════════════════════

Total game rows: 242664 (2 per game = 121332 Partien)

── Victories per Player (top 20) ────────
+-----------------+---------+
|playerName       |victories|
+-----------------+---------+
|cheesedout       |944      |
|F1_ALONSO_FERRARI|919      |
|german11         |843      |
|nichiren1967     |826      |
|Panevis          |815      |
|ChikiPuki        |742      |
...
+-----------------+---------+

── Wins per Color ───────────────────────
+------+----------+
|winner|total_wins|
+------+----------+
|white |124258    |
|black |110442    |
+------+----------+

── Average Opponent ELO Beaten (top 20) ─
+-----------------+------------------+
|playerName       |avg_bot_elo_beaten|
+-----------------+------------------+
|brahmsguitar     |2132.75           |
|screwball        |2017.89           |
|ObviousEngineUser|1989.0            |
...
+-----------------+------------------+

── Best Player: cheesedout
```

**Laufzeit:** ~29 Sekunden lokal (`local[*]`, JDK 21, Windows 11)

**Beobachtung:** Weiß gewinnt häufiger als Schwarz (124.258 vs. 110.442) — entspricht dem bekannten Anzugsvorteil im Schach (~53 % Weiß-Gewinnquote).

### Download & Ausführen

```powershell
# 1. Download (PowerShell)
Invoke-WebRequest -Uri "https://database.lichess.org/standard/lichess_db_standard_rated_2013-01.pgn.zst" -OutFile "lichess.pgn.zst"

# 2. Entpacken (7-Zip oder PeaZip)
& "C:\Program Files\7-Zip\7z.exe" e lichess.pgn.zst

# 3. Analytics starten
./gradlew :modules:spark-analytics:runLichess --args="C:\Users\<name>\Downloads\lichess_db_standard_rated_2013-01.pgn"
```

---

## Starten

### File Analytics (Schritt 1)

```bash
./gradlew :modules:spark-analytics:run
```

Optional mit eigener CSV-Datei:
```bash
./gradlew :modules:spark-analytics:run --args="/pfad/zur/datei.csv"
```

### Lichess Big Data Analytics

```bash
./gradlew :modules:spark-analytics:runLichess --args="/pfad/zur/lichess.pgn"
```

### Kafka Streaming (Schritt 2)

Voraussetzung: Kafka läuft (z.B. via `docker-compose up kafka`), chess-api produziert Züge.

```bash
./gradlew :modules:spark-analytics:runKafkaStream

# Mit eigenem Broker:
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 ./gradlew :modules:spark-analytics:runKafkaStream
```

Beenden mit **Ctrl+C**.

### Tests

```bash
./gradlew :modules:spark-analytics:test
```

---

## Vorteile

| Vorteil | Beschreibung |
|---------|-------------|
| **Horizontale Skalierbarkeit** | Spark verteilt Transformationen auf beliebig viele Worker-Nodes; die Analytik skaliert mit der Datenmenge |
| **Lazy Evaluation** | Transformationen (`filter`, `groupBy`, `agg`) werden erst bei einer Action (`show`, `count`) ausgeführt — Spark optimiert den Ausführungsplan vorher |
| **Einheitliche API** | Dieselbe `ChessAnalytics`-Logik funktioniert für Batch (CSV) und Streaming (Kafka) ohne Code-Duplikation |
| **Structured Streaming** | Kafka-Integration als Structured Streaming ist fault-tolerant (Checkpointing möglich) und nutzt dieselbe DataFrame-API wie Batch |
| **Local-Mode** | Für Entwicklung und Tests ist kein Cluster nötig (`local[*]`) — Spark nutzt alle lokalen CPU-Kerne |
| **Schema-Inferenz** | `inferSchema = true` beim CSV-Lesen erkennt Typen automatisch (Int für `botElo`, `moveCount`) |

---

## Nachteile / Einschränkungen

| Nachteil | Beschreibung |
|---------|-------------|
| **Scala 2.13 statt Scala 3** | Spark 3.5.x unterstützt Scala 3 nicht. Das Modul ist dadurch sprachlich von den anderen Services (alle Scala 3) isoliert |
| **Kein JUnit 5 Platform Engine** | Spark startet Background-Threads (ForkJoinPool, BlockManager, …), die bei Gradle 9 + JUnit 5 Platform falsche Test-Completion-Events erzeugen. Workaround: JUnit 4 Runner via `@RunWith(classOf[JUnitRunner])` |
| **`--add-opens` erforderlich** | Spark 3.5.x greift auf `sun.nio.ch.DirectBuffer` zu, das in Java 17+ standardmäßig gesperrt ist. 11 `--add-opens`-Flags müssen explizit gesetzt werden |
| **Kein Hadoop auf Windows** | Ohne `winutils.exe` gibt Spark auf Windows eine WARN-Meldung aus. Funktional unkritisch, aber laut |
| **Große Abhängigkeiten** | `spark-sql_2.13` + `spark-sql-kafka-0-10_2.13` laden ~500 MB Transitivabhängigkeiten (Hadoop, Netty, Jackson, Guava, …) |
| **Kein Kafka-Test** | `ChessKafkaStream.main()` ist nicht automatisiert testbar — es braucht einen laufenden Broker und blockiert auf `awaitTermination()`. Nur manuell testbar |
| **`complete` Outputmode** | Der ganze akkumulierte State wird bei jeder Micro-Batch gedruckt. Bei vielen gleichzeitigen Spielen wird die Konsolen-Ausgabe groß. Für Produktion wäre `append`-Mode mit Windowing besser |
| **Kein Checkpointing** | Der Kafka-Stream hat keinen Checkpoint-Pfad — bei Neustart beginnt er von `latest` (kein Replay von verpassten Nachrichten) |

---

## Technische Details

### Warum Spark 3.5.3 und nicht 4.x?

Die Vorlesungsfolien nennen 4.1.2, aber Spark 4.x erfordert Scala 2.13 und bringt API-Breaking-Changes im Streaming-Bereich mit sich (v.a. für `kafka-0-10`). Spark 3.5.3 ist die aktuelle LTS-Version, hat stabile Scala-2.13-Builds auf Maven Central und ist bestens dokumentiert.

### Warum `spark-sql-kafka-0-10` und nicht `spark-streaming-kafka-0-10`?

Die alte `DStream`-API (`spark-streaming-kafka-0-10`) ist seit Spark 3.x deprecated. **Structured Streaming** (`spark-sql-kafka-0-10`) ist der empfohlene Nachfolger:  
- Nutzt die DataFrame/Dataset API
- Unterstützt `watermark`, `window`, `groupBy` direkt
- Fault-tolerant durch Checkpointing
- Gleiche Query-Planung wie Batch

### Datenfluss Kafka-Stream

```
Kafka Topic: chess.move-requests
  │  key=gameId, value=JSON {gameId, fen, color, elo}
  │
  ▼
readStream.format("kafka")
  │  columns: key, value (binary), topic, partition, offset, timestamp
  │
  ▼
from_json(col("value").cast("string"), MoveRequestSchema)
  │  parst JSON zu Spalten: gameId, fen, color, elo
  │
  ▼
groupBy("gameId", "color").agg(count(*), avg("elo"))
  │
  ▼
writeStream.format("console").outputMode("complete")
```

---

## Verbindung zu anderen Modulen

| Modul | Verbindung |
|-------|-----------|
| **chess-api** | Produziert `chess.move-requests` via `BotMoveKafkaProducer` (gameId, fen, color, elo) |
| **bot-service** | Konsumiert `chess.move-requests`, antwortet auf `chess.bot-responses` |
| **spark-analytics** | Konsumiert **zusätzlich** `chess.move-requests` (read-only, eigene Consumer Group) |

Das Spark-Modul ist ein **reiner Consumer** — es verändert keine Daten und hat keinen Einfluss auf das laufende Spiel.

---

## Weiterführende Links

- [Kafka-Architektur](../kafka.md) — Topic-Schema, Producer/Consumer-Übersicht
- [Microservices](microservices.md) — Gesamtübersicht aller Services
- [Apache Spark Dokumentation](https://spark.apache.org/docs/3.5.3/)
- [Spark Structured Streaming + Kafka](https://spark.apache.org/docs/3.5.3/structured-streaming-kafka-integration.html)
