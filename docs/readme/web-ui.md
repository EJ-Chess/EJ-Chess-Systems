# Web-UI — `modules/chess-ui`

Moderne Browser-Oberfläche für EJa Chess.  
React/TypeScript SPA, kommuniziert ausschließlich über REST mit dem Game-Service.

---

## Starten

```bash
cd modules/chess-ui
npm install
npm run dev        # → http://localhost:5173
```

Voraussetzung: Game-Service läuft auf Port 8080.  
Für Bot-Partien zusätzlich Bot-Service auf Port 8081.

---

## Funktionen

### Spielmodus-Auswahl (`GameSetupModal`)

Beim Klick auf „Neues Spiel" öffnet sich ein Dialog:

| Einstellung | Optionen |
|-------------|----------|
| Gegner | Mensch / Bot |
| Farbe (nur bei Bot) | Weiß / Schwarz |
| Bot-Stärke (nur bei Bot) | 800 / 1000 / 1200 / 1400 / 1600 / 1800 / 2000 ELO |

### Spielbrett

- Drag & Drop sowie Klick-Navigation
- Legale Züge werden optisch hervorgehoben
- Brett dreht sich bei Schwarz-Wahl automatisch (`orientation`)
- Bei Bot-Partien ist das Brett während des Bot-Zugs gesperrt

### Schachuhr

- Standard: 10 Minuten pro Spieler
- Einstellbar über das Zahnrad-Icon (Vorgaben: 1 / 3 / 5 / 10 / 15 / 30 min)

### Import / Export

| Funktion | Beschreibung |
|----------|--------------|
| FEN exportieren | Kopiert die aktuelle Stellung als FEN-String |
| FEN importieren | Lädt eine Stellung aus einem FEN-String |
| PGN exportieren | Kopiert die vollständige Zugfolge als PGN |
| PGN importieren | Replays eine PGN-Partie |

### Speichern beim Verlassen

Klick auf das Logo → Dialog „Spiel verlassen?":
- **Ja, speichern** — lädt die Partie als `.pgn`-Datei herunter (oder `.fen` falls noch keine Züge)
- **Nein, verwerfen** — Partie wird verworfen, zurück zur Startseite
- **Abbrechen** — Dialog schließen, Partie läuft weiter

### Zughistorie & Log

- Kompakte Zughistorie in der Seitenleiste
- Klick → vollständiges Zugprotokoll als Modal

---

## API-Anbindung (`src/api/chessApi.ts`)

```typescript
chessApi.createGame({ opponent: 'bot', playerColor: 'white', botElo: 1400 })
chessApi.makeMove(gameId, { from: 'e2', to: 'e4' })
chessApi.undo(gameId)
chessApi.redo(gameId)
chessApi.importGame(gameId, { pgn: '...' })
chessApi.getFen(gameId)
chessApi.getPgn(gameId)
```

---

## Tests

```bash
./gradlew :modules:chess-ui:npmTest
# oder direkt:
cd modules/chess-ui && npm test
```

| Testdatei | Inhalt |
|-----------|--------|
| `chessApi.test.ts` | API-Client — alle Endpunkte inkl. `createGame` mit Bot-Optionen |
| `GameSetupModal.test.tsx` | Spielmodus-Dialog — Rendering, Interaktion, Callbacks |
| `GameControls.test.tsx` | Steuerungsleiste — Buttons, Zustände |
| `ImportExportModal.test.tsx` | Import/Export-Dialog |
| `utils.test.ts` | Hilfsfunktionen |

---

## Technologien

| Paket | Version | Zweck |
|-------|---------|-------|
| React | 18.3 | UI-Framework |
| TypeScript | 5.7 | Typsicherheit |
| Vite | 5.4 | Build / Dev-Server |
| react-chessboard | 4.7 | Schachbrett-Komponente |
| chess.js | 1.3 | Lokale Zugvalidierung (UI-seitig) |
| Tailwind CSS | 3.4 | Styling |
| Radix UI | — | Dialoge, Tabs |
| sonner | 1.7 | Toast-Benachrichtigungen |
| Vitest | 2.1 | Test-Runner |
| MSW | — | API-Mocking in Tests |
