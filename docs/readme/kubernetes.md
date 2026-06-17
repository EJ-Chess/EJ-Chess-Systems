# Kubernetes Deployment (k3d) auf dem Uni-Server

Die Plattform läuft auf `141.37.123.121` als k3d-Cluster (k3s in Docker — kein Root nötig).

**Zugangsdaten:** `ssh chess@141.37.123.121` → Passwort in `credentials.txt`

---

## Cluster-Übersicht

| Komponente | Detail |
|-----------|--------|
| Tool | k3d v5 (k3s in Docker-Containern) |
| Cluster-Name | `eljachess` |
| Namespace | `chess` |
| Web-UI Port | `30080` |
| pgAdmin Port | `30050` |
| kubectl | `docker exec k3d-eljachess-server-0 kubectl` |

---

## URLs (über VPN)

| URL | Dienst |
|-----|--------|
| http://141.37.123.121:30080 | Web-UI |
| http://141.37.123.121:30050 | pgAdmin (admin@chess.com / admin) |

---

## Deployment aktualisieren (nach Code-Änderungen)

### Schritt 1 — JARs lokal bauen

```bash
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild
```

### Schritt 2 — Docker Images lokal bauen

> **Wichtig:** Image-Namen müssen mit den k8s-Manifesten übereinstimmen (`eljachess/` Prefix).

```bash
docker build -t eljachess/chess-api:latest  modules/chess-api
docker build -t eljachess/bot-service:latest modules/bot-service
docker build -t eljachess/chess-ui:latest   modules/chess-ui
```

### Schritt 3 — Images zum Server übertragen und in k3d importieren

```bash
docker save eljachess/chess-api:latest eljachess/bot-service:latest eljachess/chess-ui:latest \
  | gzip \
  | ssh chess@141.37.123.121 \
    "gunzip | docker load && ~/.local/bin/k3d image import eljachess/chess-api:latest eljachess/bot-service:latest eljachess/chess-ui:latest --cluster eljachess"
```

### Schritt 4 — Manifeste anwenden (nur bei YAML-Änderungen nötig)

```bash
ssh chess@141.37.123.121 "cd ~/EJ-Chess-Systems && git pull && \
  docker exec k3d-eljachess-server-0 kubectl apply -f k8s/"
```

### Schritt 5 — Pods neu starten (neue Images laden)

```bash
ssh chess@141.37.123.121 "
  docker exec k3d-eljachess-server-0 kubectl rollout restart deployment/game-service -n chess
  docker exec k3d-eljachess-server-0 kubectl rollout restart deployment/bot-service  -n chess
  docker exec k3d-eljachess-server-0 kubectl rollout restart deployment/chess-ui     -n chess
"
```

### Schritt 6 — Status prüfen

```bash
ssh chess@141.37.123.121 "docker exec k3d-eljachess-server-0 kubectl get pods -n chess"
```

Erwartete Ausgabe (alle `Running`):
```
NAME                            READY   STATUS    RESTARTS   AGE
bot-service-xxx                 1/1     Running   0          1m
chess-ui-xxx                    1/1     Running   0          1m
game-service-xxx                1/1     Running   0          1m
postgres-xxx                    1/1     Running   0          13d
```

---

## Erstes Setup (einmalig — Cluster existiert bereits)

Der Cluster ist bereits vorhanden. Diese Schritte nur bei komplettem Neustart nötig.

```bash
ssh chess@141.37.123.121

# k3d und kubectl sind in ~/.local/bin
export PATH="$HOME/.local/bin:$PATH"

# Cluster-Status prüfen
k3d cluster list

# Falls Cluster nicht läuft, starten
k3d cluster start eljachess

# kubeconfig setzen
mkdir -p ~/.kube
k3d kubeconfig get eljachess > ~/.kube/config

# Manifeste anwenden
cd ~/EJ-Chess-Systems
docker exec k3d-eljachess-server-0 kubectl apply -f k8s/
```

---

## Logs & Debugging

```bash
# Alle Pods anzeigen
ssh chess@141.37.123.121 "docker exec k3d-eljachess-server-0 kubectl get pods -n chess"

# Logs eines Services
ssh chess@141.37.123.121 "docker exec k3d-eljachess-server-0 kubectl logs -n chess deployment/game-service"

# Live-Logs
ssh chess@141.37.123.121 "docker exec k3d-eljachess-server-0 kubectl logs -n chess -f deployment/game-service"

# Pod Details (bei Problemen)
ssh chess@141.37.123.121 "docker exec k3d-eljachess-server-0 kubectl describe pod -n chess <pod-name>"
```

---

## Häufige Probleme

### Bot antwortet nicht
`KAFKA_ENABLED=false` muss in der ConfigMap gesetzt sein (kein Kafka im Cluster).
```bash
ssh chess@141.37.123.121 "docker exec k3d-eljachess-server-0 kubectl get configmap chess-config -n chess -o yaml"
```

### Pod bleibt in `Pending`
```bash
ssh chess@141.37.123.121 "docker exec k3d-eljachess-server-0 kubectl describe pod -n chess <pod-name>"
```
Meist: Image nicht in k3d importiert (`imagePullPolicy: Never` → Image muss lokal im Cluster sein).

### Image-Import prüfen
```bash
ssh chess@141.37.123.121 "docker exec k3d-eljachess-server-0 crictl images | grep eljachess"
```

### Alles neu starten
```bash
ssh chess@141.37.123.121 "
  docker exec k3d-eljachess-server-0 kubectl rollout restart deployment -n chess
"
```

### Namespace komplett neu anlegen
```bash
ssh chess@141.37.123.121 "
  docker exec k3d-eljachess-server-0 kubectl delete namespace chess
  cd ~/EJ-Chess-Systems && docker exec k3d-eljachess-server-0 kubectl apply -f k8s/
"
```
