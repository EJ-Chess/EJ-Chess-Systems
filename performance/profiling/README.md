# Flame Graph — Java Flight Recorder (JFR) + JDK Mission Control

## Was du brauchst

| Tool | Download | Preis |
|------|----------|-------|
| JDK Mission Control (JMC) | https://adoptium.net/jmc/ | kostenlos |
| k6 | https://github.com/grafana/k6/releases/latest (MSI) | kostenlos |

JFR ist in deinem JDK 25 bereits eingebaut — kein extra Install nötig.

---

## Schritt-für-Schritt

### 1. API mit JFR-Recording starten (PowerShell)

```powershell
# JFR-Flags als Umgebungsvariable setzen
$env:JAVA_TOOL_OPTIONS = "-XX:StartFlightRecording=duration=90s,filename=modules/chess-api/build/chess-api.jfr,settings=profile,name=chess-api"

# API starten
./gradlew :modules:chess-api:quarkusDev

# Nach dem Test: Variable wieder entfernen
Remove-Item Env:JAVA_TOOL_OPTIONS
```

Die Aufzeichnung startet automatisch sobald die API hochgefahren ist.

### 2. Last erzeugen (neues Terminal, gleichzeitig)

```powershell
# k6-Lasttest starten (braucht k6 installiert)
k6 run performance/k6/chess-api.js

# ODER Locust (braucht Python + locust)
locust -f performance/locust/locustfile.py --host=http://localhost:8080 --headless -u 10 -r 2 --run-time 60s
```

Nach 90 Sekunden stoppt die JFR-Aufzeichnung automatisch.
Die Datei liegt dann unter `modules/chess-api/build/chess-api.jfr`.

### 3. Flame Graph in JDK Mission Control öffnen

1. JMC starten (nach dem Download: `jmc.exe` im entpackten Ordner)
2. `File → Open File` → `modules/chess-api/build/chess-api.jfr` auswählen
3. Links im Baum: `chess-api` → `Method Profiling` klicken
4. Oben rechts: **"Flame Graph"** Tab auswählen

### 4. Bottleneck identifizieren

Im Flame Graph siehst du welche Methoden am meisten CPU-Zeit verbrauchen.
Typisch bei der Chess API (vor der `legalMoves`-Optimierung):

```
Board.legalMoves        ████████████████ 45%
  Board.isInCheck       ████████████     30%
    Board.isValidRookMove ...
    Board.isValidBishopMove ...
```

Der breite Block bei `isInCheck` zeigt: für jeden Kandidaten-Zug wird
der komplette gegnerische Angriff neu berechnet.

---

## Vorher / Nachher zeigen

```
Baseline  (vor Board.legalMoves-Optimierung):
  → legalMoves Block nimmt ~45% des Flame Graph ein

Nach Optimierung (Proposal A):
  → legalMoves Block schrumpft auf ~15%
```

Das ist visueller Beweis für die algorithmische Verbesserung.

---

## Tipp: JFR ohne JMC auswerten

```powershell
# Überblick über die Aufzeichnung (JDK built-in)
& "C:\Program Files\Java\jdk-25\bin\jfr.exe" summary modules/chess-api/build/chess-api.jfr

# Top-Methoden nach CPU-Zeit
& "C:\Program Files\Java\jdk-25\bin\jfr.exe" print --events ExecutionSample modules/chess-api/build/chess-api.jfr | Select-Object -First 50
```
