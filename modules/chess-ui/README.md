# chess-ui

React-Frontend für EJa Chess. Kommuniziert über einen Vite-Dev-Proxy mit dem `chess-api`-Backend.

## Voraussetzungen

- Node.js 18+
- Das `chess-api`-Backend läuft auf `http://localhost:8080`

## Backend starten

Im Repository-Root:

```bash
./gradlew :modules:chess-api:quarkusDev
```

Das Backend läuft dann auf `http://localhost:8080`.

## Frontend starten

```bash
cd modules/chess-ui
npm install      # nur beim ersten Mal nötig
npm run dev
```

Die UI ist danach unter `http://localhost:5173` erreichbar.  
Vite leitet alle `/games`-Requests automatisch an `http://localhost:8080` weiter.

## Tests

```bash
npm test                  # einmalig ausführen
npm run test:watch        # im Watch-Modus
npm run test:coverage     # mit Coverage-Report
```

## Build (Produktion)

```bash
npm run build    # Output landet in dist/
npm run preview  # lokale Vorschau des Builds
```
