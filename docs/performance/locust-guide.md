# Locust Load Test — Guide

**Script:** `performance/locust/locustfile.py`
**Tool:** [Locust](https://locust.io) — Python-basierter Load-Tester mit Web-UI

## Installation

```powershell
pip install locust
# prüfen:
locust --version
```

Falls `pip` nicht vorhanden: Python von https://python.org installieren (Windows-Installer, "Add to PATH" aktivieren).

## Starten (mit Web-UI)

```powershell
# Terminal 1: API starten
./gradlew :modules:chess-api:quarkusDev

# Terminal 2: Locust starten
locust -f performance/locust/locustfile.py --host=http://localhost:8080
```

Dann Browser öffnen: **http://localhost:8089**

Im UI eingeben:
- **Number of users:** 10
- **Spawn rate:** 2  (Nutzer pro Sekunde hochfahren)
- **Host:** http://localhost:8080 (schon vorausgefüllt)
- → **Start swarming** klicken

## Starten (ohne UI, für CI/Skript)

```powershell
locust -f performance/locust/locustfile.py `
  --host=http://localhost:8080 `
  --headless `
  --users 10 `
  --spawn-rate 2 `
  --run-time 60s `
  --html performance/locust/report.html
```

Report liegt danach unter `performance/locust/report.html`.

## Was das Szenario testet

| Task | Gewicht | Endpoint |
|------|---------|----------|
| Spielstand lesen | 4× | `GET /games/{id}` |
| Legale Züge lesen | 3× | `GET /games/{id}/moves` |
| Zug machen + rückgängig | 2× | `POST /moves` + `POST /undo` |
| Spiel erstellen + löschen | 1× | `POST /games` + `DELETE` |

Das Verhältnis 4:3:2:1 simuliert reales Nutzerverhalten (viel lesen, wenig schreiben).

## Unterschied zu k6

| | k6 | Locust |
|---|---|---|
| Sprache | JavaScript | Python |
| UI | CLI-only | **Web-UI mit Live-Graphen** |
| Thresholds | statisch in Script | dynamisch im UI einstellbar |
| Szenario-Gewichtung | explizit | `@task(weight)` |
| Gut für | CI/CD-Gate | **Demo, Präsentation, Exploration** |

## Thresholds manuell prüfen (UI)

Im Locust Web-UI siehst du in Echtzeit:
- **RPS** (Requests per Second)
- **Failures** (sollte 0 bleiben)
- **Response time** p50 / p95 / p99 (p95 sollte < 200 ms bleiben)

Klick auf **"Charts"** Tab für Live-Graphen — ideal für Screenshots in der Abgabe.
