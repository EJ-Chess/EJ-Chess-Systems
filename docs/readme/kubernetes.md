# Kubernetes Deployment (k3s) auf dem Uni-Server

Diese Anleitung erklärt, wie die EJa Chess Services auf `aim-chess-2` mit k3s (Lightweight Kubernetes) deployed werden.

---

## Schritt 1: k3s auf dem Server installieren

SSH auf den Server:
```bash
ssh chess@141.37.74.141
```

k3s installieren:
```bash
curl -sfL https://get.k3s.io | sh -
```

kubectl-Zugriff ohne sudo konfigurieren:
```bash
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $USER ~/.kube/config
sudo chmod 600 ~/.kube/config
```

Prüfen, dass es funktioniert:
```bash
kubectl get nodes
```

Output sollte ähnlich aussehen:
```
NAME          STATUS   ROLES                  AGE   VERSION
aim-chess-2   Ready    control-plane,master   1m    v1.xx.x
```

---

## Schritt 2: Docker Images bauen und zum Server übertragen

**Lokal auf eurem Rechner** (nicht auf dem Server!):

### 2a. Quarkus fast-JARs bauen
```bash
cd /c/HTWG/Bachelor/SS26/Software\ Architektur\ \(SA\)/EJ-Chess-Systems
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild
```

### 2b. Docker Images bauen
```bash
cd modules/chess-api
docker build -t eja-chess/game-service:latest .
cd ../bot-service
docker build -t eja-chess/bot-service:latest .
cd ../chess-ui
docker build -t eja-chess/chess-ui:latest .
```

### 2c. Images zum Server übertragen
```bash
# Lokal: Alle drei Images in eine Datei packen und mit gzip komprimieren
docker save eja-chess/game-service:latest eja-chess/bot-service:latest eja-chess/chess-ui:latest \
  | gzip | ssh chess@141.37.74.141 "gunzip | sudo k3s ctr images import -"
```

Das kann 1-2 Minuten dauern. Wenn es fertig ist, seid ihr fertig mit den Images.

---

## Schritt 3: Kubernetes Manifeste auf dem Server anwenden

SSH auf dem Server (falls noch nicht verbunden):
```bash
ssh chess@141.37.74.141
```

Manifest-Verzeichnis vom Git clonen oder hochladen. Annahme: die `k8s/` Verzeichnis-Struktur existiert auf dem Server unter `~/chess-manifests/`:

```bash
# Namespace + Secret + ConfigMap anwenden
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml

# PostgreSQL
kubectl apply -f k8s/postgres/pvc.yaml
kubectl apply -f k8s/postgres/deployment.yaml
kubectl apply -f k8s/postgres/service.yaml

# Bot-Service (wartet auf nichts)
kubectl apply -f k8s/bot-service/deployment.yaml
kubectl apply -f k8s/bot-service/service.yaml

# Game-Service (wartet auf bot-service healthy)
kubectl apply -f k8s/game-service/deployment.yaml
kubectl apply -f k8s/game-service/service.yaml

# Chess-UI (wartet auf game-service)
kubectl apply -f k8s/chess-ui/deployment.yaml
kubectl apply -f k8s/chess-ui/service.yaml

# pgAdmin (optional)
kubectl apply -f k8s/pgadmin/deployment.yaml
kubectl apply -f k8s/pgadmin/service.yaml
```

---

## Schritt 4: Status prüfen

Alle Pods sollten innerhalb von ~2 Minuten in `Running` Status sein:

```bash
kubectl get pods -n chess -w
```

`-w` heißt "watch" — drücke Ctrl+C zum Beenden.

Output beispielsweise:
```
NAME                      READY   STATUS    RESTARTS   AGE
postgres-5f7b8c6d4-xyz    1/1     Running   0          60s
bot-service-abc123-xyz    1/1     Running   0          50s
game-service-def456-xyz   1/1     Running   0          40s
chess-ui-ghi789-xyz       1/1     Running   0          30s
pgadmin-jkl012-xyz        1/1     Running   0          25s
```

Alle Services sollten erreichbar sein:

```bash
kubectl get services -n chess
```

Output:
```
NAME            TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)           AGE
postgres        ClusterIP   10.43.x.x       <none>        5432/TCP          60s
bot-service     ClusterIP   10.43.x.x       <none>        8081/TCP          50s
game-service    ClusterIP   10.43.x.x       <none>        8080/TCP          40s
chess-ui        NodePort    10.43.x.x       <none>        80:30080/TCP      30s
pgadmin         NodePort    10.43.x.x       <none>        80:30050/TCP      25s
```

---

## Schritt 5: Von außen zugreifen

Außerhalb des Servers (z.B. vom eigenen Laptop über VPN):

**Web-UI:**
```
http://141.37.74.141:30080
```

**pgAdmin** (optional):
```
http://141.37.74.141:30050
```
Anmeldung: `admin@chess.com` / `admin`

In pgAdmin kannst du die Postgres-Datenbank konfigurieren:
- Hostname: `postgres` (Service-Name innerhalb des Clusters)
- Port: `5432`
- Database: `chess`
- Username: `chess`
- Password: `chess`

---

## Logs anschauen

Wenn etwas nicht läuft, schaut euch die Logs an:

```bash
# Alle Logs im chess-Namespace
kubectl logs -n chess --all-containers=true -l app=<service-name>

# Logs eines bestimmten Pods
kubectl logs -n chess deployment/game-service

# Live-Logs (wie `tail -f`)
kubectl logs -n chess -f deployment/game-service

# Logs eines bestimmten Containers wenn Pod mehrere hat
kubectl logs -n chess deployment/game-service -c game-service
```

---

## Pods neu starten (falls nötig)

```bash
# Einen Pod löschen — Deployment erstellt ihn neu
kubectl delete pod -n chess <pod-name>

# Alle Pods eines Services neu starten
kubectl rollout restart deployment/game-service -n chess
```

---

## Manifeste aktualisieren

Wenn ihr Code ändert:

1. **Lokal neu bauen:**
   ```bash
   ./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild
   docker build -t eja-chess/game-service:latest modules/chess-api
   docker build -t eja-chess/bot-service:latest modules/bot-service
   docker build -t eja-chess/chess-ui:latest modules/chess-ui
   ```

2. **Neue Images zum Server:**
   ```bash
   docker save eja-chess/game-service:latest eja-chess/bot-service:latest eja-chess/chess-ui:latest \
     | gzip | ssh chess@141.37.74.141 "gunzip | sudo k3s ctr images import -"
   ```

3. **Pods neu starten (damit die neuen Images geladen werden):**
   ```bash
   ssh chess@141.37.74.141
   kubectl rollout restart deployment/game-service -n chess
   kubectl rollout restart deployment/bot-service -n chess
   kubectl rollout restart deployment/chess-ui -n chess
   ```

---

## Alles wieder löschen

Falls ihr komplett von vorne anfangen wollt:

```bash
kubectl delete namespace chess
```

Das löscht:
- Alle Pods
- Alle Services
- Alle Deployments
- Die PostgreSQL-Daten (PVC)

---

## Troubleshooting

### Pod bleibt im "Pending"-Status
Cluster-Resources zu klein. Aber für einen Server mit 4+ GB RAM sollte das kein Problem sein.

### Pod crasht immer wieder
```bash
kubectl describe pod -n chess <pod-name>
```
Schaut euch die "Events" am Ende an — dort sieht man den Grund.

### Datenbank-Fehler im game-service
Postgres braucht ein paar Sekunden zum Hochfahren. game-service wartet per `readinessProbe` bis Postgres ready ist. Falls das nicht funktioniert:

```bash
kubectl logs -n chess deployment/postgres
```

### Web-UI lädt Seite aber keine Daten vom Game-Service
Das liegt meist daran, dass nginx nicht richtig an game-service verbunden ist. Aktuell ist in der nginx.conf hardcodiert:
```
proxy_pass http://game-service:8080;
```

Das sollte funktionieren, weil `game-service` der Name des K8s Service ist.

---

## Nächste Schritte

- Monitoring: `kubectl top nodes` / `kubectl top pods`
- Ingress-Controller für richtige DNS-Namen statt NodePorts
- Persistent volumes richtig konfigurieren (aktuell: lokal auf dem Server)
