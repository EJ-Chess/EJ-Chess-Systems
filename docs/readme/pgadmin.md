# pgAdmin — PostgreSQL Web-UI

pgAdmin ist nur verfügbar wenn Docker mit `--profile db` gestartet wurde.

```bash
docker-compose --profile db up --build -d
```

---

## Anmelden

**http://localhost:5050**

| Feld | Wert |
|------|------|
| Email | `admin@chess.com` |
| Password | `admin` |

---

## Datenbankverbindung einrichten

Beim ersten Login muss die Verbindung zur PostgreSQL-Instanz einmalig eingerichtet werden.

1. Linke Sidebar → **Servers** → Rechtsklick → **Register → Server…**
2. Tab **General**: Name: `Chess DB` (beliebig)
3. Tab **Connection**:

| Feld | Wert |
|------|------|
| Host | `postgres` |
| Port | `5432` |
| Maintenance database | `chess` |
| Username | `chess` |
| Password | `chess` |

4. **Save** klicken

Die Verbindung erscheint jetzt links unter **Servers → Chess DB**.

---

## Partien direkt abfragen (Query Tool)

**Servers → Chess DB → Databases → chess → Schemas → public → Tables → games** → Rechtsklick → **Query Tool**

Oder: Menü oben → **Tools → Query Tool**

### Alle Partien anzeigen

```sql
SELECT * FROM games;
```

### Nur laufende Partien (Bot-Spiele)

```sql
SELECT id, player_color, bot_color, bot_elo
FROM games
WHERE bot_color IS NOT NULL
ORDER BY id;
```

### PGN einer bestimmten Partie lesen

```sql
SELECT id, pgn
FROM games
WHERE id = 'deine-game-id-hier';
```

### Anzahl Partien

```sql
SELECT COUNT(*) AS total_games FROM games;
```

### Persistenz-Nachweis nach Neustart

```sql
-- Vor dem Neustart: IDs merken
SELECT id FROM games;

-- docker-compose restart game-service
-- Dann nochmal abfragen — Zeilen sind noch da
SELECT id FROM games;
```

---

## Tabellenstruktur ansehen

**Schemas → public → Tables → games** → Rechtsklick → **Properties**

Oder per SQL:

```sql
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'games'
ORDER BY ordinal_position;
```

---

## Daten als CSV exportieren

1. Query Tool → gewünschtes SELECT ausführen
2. Ergebnis-Tab → **Download** (Pfeil-Icon oben rechts im Result-Panel)
3. Als `.csv` speichern

---

## Daten live beobachten (während Web-UI läuft)

1. Web-UI öffnen → neues Bot-Spiel starten
2. pgAdmin Query Tool → `SELECT * FROM games;` ausführen
3. Zeile erscheint sofort nach Spielerstellung ✓
4. Züge machen → `SELECT pgn FROM games WHERE id = '...'` → PGN wächst mit jedem Zug

---

## Tabelle leeren (für Tests)

```sql
-- Achtung: löscht alle Partien unwiderruflich
DELETE FROM games;
```

---

## Verbindung zu pgAdmin bleibt erhalten

pgAdmin speichert die Server-Verbindung in einem Docker-Volume — nach einem Neustart mit `--profile db` ist die Verbindung **Chess DB** automatisch wieder da, ohne erneut einzurichten.
