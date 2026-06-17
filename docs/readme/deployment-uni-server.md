# Deployment auf dem Uni-Server (aim-chess-2)

Dieses Dokument beschreibt das tatsächliche Deployment der EJa Chess Platform auf dem Uni-Server `aim-chess-2` (IP: `141.37.123.121`).

---

## Herausforderung: Kein sudo auf dem Uni-Server

Der Uni-Server stellt eine eingeschränkte Umgebung dar. Der zugewiesene User `chess` hat **keine Root-Rechte (`sudo`)**.

Das bedeutet konkret:

- **k3s nativ installieren geht nicht** — `curl -sfL https://get.k3s.io | sh -` benötigt sudo zum Schreiben nach `/usr/local/bin`, `/etc/rancher/` und für systemd-Services.
- **kubectl installieren geht nicht** — systemweite Installation erfordert sudo.
- **Portfreigaben in der Firewall** — nicht möglich, nur Ports 80, 443 und 22 sind von der Uni-Firewall nach außen geöffnet.

### Lösung: k3d (k3s in Docker)

Da Docker vorinstalliert ist und der User in der `docker`-Gruppe ist, kann Docker **ohne sudo** genutzt werden. Darauf aufbauend löst **k3d** das Problem:

> k3d ist ein Wrapper, der einen vollständigen k3s-Kubernetes-Cluster als Docker-Container startet — ohne Root, ohne systemd, ohne native Installation.

```
Eigener Rechner          Uni-Server (chess@141.37.123.121)
─────────────            ─────────────────────────────────────────────
docker build    ──►      Docker (ohne sudo, chess in docker-Gruppe)
docker save     ──►        └── k3d-eljachess-server-0  (k3s control plane)
ssh + import             ├── k3d-eljachess-agent-0    (worker node)
                         ├── k3d-eljachess-agent-1    (worker node)
                         └── k3d-eljachess-serverlb   (load balancer → Port 30080)
                                                              │
                         nginx chess-proxy (Port 80) ────────┘
                                                              │
VPN ──► http://141.37.123.121 ◄────────────────────────────────
```

Alle `kubectl`-Befehle werden über `docker exec k3d-eljachess-server-0 kubectl ...` ausgeführt, da `kubectl` ebenfalls nicht systemweit installiert werden kann.

---

## Voraussetzungen

- VPN-Verbindung zur HTWG aktiv
- SSH-Zugang: `chess@141.37.123.121` (Passwort in `credentials.txt / pw.txt`)
- Lokale Docker-Installation zum Bauen der Images
- `credentials.txt / pw.txt` und READMEs dürfen **nicht** auf den Server übertragen werden

---

## Rahmenbedingungen des Servers

| Eigenschaft | Wert |
|-------------|------|
| Betriebssystem | Ubuntu Linux |
| Freie Ports (Uni-Firewall) | **22, 80, 443** — alle anderen Ports sind gesperrt |
| Docker | v29.4.0 (vorinstalliert) |
| Rechte | Kein `sudo`, kein natives k3s/kubectl installierbar |
| User | `chess` (Gruppe: `docker`) |
| Disk | ~31 GB gesamt, ~8 GB frei |

> k3s wird **nicht** nativ installiert, da kein `sudo` verfügbar. Kubernetes läuft stattdessen über **k3d** (k3s in Docker).

---

## Cluster-Setup (einmalig)

### Schritt 1: SSH verbinden

```bash
ssh chess@141.37.123.121
```

### Schritt 2: k3d installieren (ohne sudo, ins Home-Verzeichnis) SHell Skript:

```bash
curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | TAG=v5.9.0 bash
# Falls kein sudo: ins Home-Verzeichnis installieren
mkdir -p ~/.local/bin
curl -Lo ~/.local/bin/k3d https://github.com/k3d-io/k3d/releases/download/v5.9.0/k3d-linux-amd64
chmod +x ~/.local/bin/k3d
export PATH="$HOME/.local/bin:$PATH"
```

### Schritt 3: k3d-Cluster erstellen

Der Cluster wird mit Port-Mappings für **30080** (Web-UI) und **30050** (pgAdmin) erstellt:

```bash
k3d cluster create eljachess \
  -p "30080:30080@loadbalancer" \
  -p "30050:30050@loadbalancer" \
  --agents 2
```

### Schritt 4: kubectl konfigurieren

```bash
mkdir -p ~/.kube
k3d kubeconfig get eljachess > ~/.kube/config
chmod 600 ~/.kube/config
```

Prüfen:
```bash
docker exec k3d-eljachess-server-0 kubectl get nodes
```

---

## Docker Images bauen und übertragen

### Lokal (auf dem eigenen Rechner):

```bash
# 1. Quarkus JARs bauen
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild

# 2. Docker Images bauen
docker build -t eljachess/chess-api:latest modules/chess-api
docker build -t eljachess/bot-service:latest modules/bot-service
docker build -t eljachess/chess-ui:latest modules/chess-ui

# 3. Images zum Server übertragen und direkt in k3d importieren
docker save eljachess/chess-api:latest eljachess/bot-service:latest eljachess/chess-ui:latest \
  | gzip | ssh chess@141.37.123.121 \
  "gunzip | docker exec -i k3d-eljachess-server-0 ctr images import -"
```

---

## Kubernetes Manifeste deployen

Auf dem Server (oder per SSH):

```bash
docker exec k3d-eljachess-server-0 kubectl apply -f k8s/namespace.yaml
docker exec k3d-eljachess-server-0 kubectl apply -f k8s/secret.yaml
docker exec k3d-eljachess-server-0 kubectl apply -f k8s/configmap.yaml
docker exec k3d-eljachess-server-0 kubectl apply -f k8s/postgres/
docker exec k3d-eljachess-server-0 kubectl apply -f k8s/bot-service/
docker exec k3d-eljachess-server-0 kubectl apply -f k8s/game-service/
docker exec k3d-eljachess-server-0 kubectl apply -f k8s/chess-ui/
```

Status prüfen:

```bash
docker exec k3d-eljachess-server-0 kubectl get pods -n chess
```

---

## Nginx-Proxy auf Port 80 (wichtig!)

Da die Uni-Firewall nur Port 80 nach außen durchlässt, der k3d-Cluster aber auf Port 30080 läuft, wird ein Nginx-Proxy-Container benötigt.

### Einmalig einrichten:

```bash
# Config-Datei erstellen
cat > ~/chess-proxy.conf << 'EOF'
server {
    listen 80;
    location / {
        proxy_pass http://localhost:30080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 300;
        proxy_connect_timeout 300;
    }
}
EOF

# Proxy-Container starten (startet nach Server-Neustart automatisch neu)
docker run -d \
  --name chess-proxy \
  --network host \
  --restart unless-stopped \
  -v ~/chess-proxy.conf:/etc/nginx/conf.d/default.conf \
  nginx:alpine
```

### Prüfen:

```bash
curl -o /dev/null -w "%{http_code}" http://localhost:80
# Erwartete Ausgabe: 200
```

---

## Aktueller Cluster-Status (Stand: 09.06.2026)

| Service | Status |
|---------|--------|
| postgres | Running |
| bot-service | Running |
| game-service | Running |
| chess-ui | Running |
| pgAdmin | CrashLoopBackOff (wird nicht benötigt) |
| chess-proxy (nginx) | Running |

---

## Erreichbarkeit (über VPN)

| URL | Dienst |
|-----|--------|
| `http://141.37.123.121` | Web-UI (über nginx-Proxy auf Port 80) |
| `http://141.37.123.121:30080` | Web-UI direkt (nur im selben Netz) |

---

## Status prüfen (per SSH)

```bash
# Alle laufenden Container
docker ps

# Pods im chess-Namespace
docker exec k3d-eljachess-server-0 kubectl get pods -n chess

# Logs eines Services
docker exec k3d-eljachess-server-0 kubectl logs -n chess deployment/game-service

# Nginx-Proxy Logs
docker logs chess-proxy
```

---

## Pods neu starten (nach Image-Update)

```bash
docker exec k3d-eljachess-server-0 kubectl rollout restart deployment/game-service -n chess
docker exec k3d-eljachess-server-0 kubectl rollout restart deployment/bot-service -n chess
docker exec k3d-eljachess-server-0 kubectl rollout restart deployment/chess-ui -n chess
```

---

## Troubleshooting

### Web-UI nicht erreichbar von außen
Port 30080 ist von der Uni-Firewall gesperrt. Nur Port 80, 443 und 22 sind offen.  
→ Sicherstellen dass `chess-proxy` läuft: `docker ps | grep chess-proxy`  
→ Falls nicht: `docker start chess-proxy`

### chess-proxy startet nicht (Port 80 belegt)
Ein anderer Container (z.B. `k3d-k3d-cluster-serverlb`) belegt Port 80.  
→ `docker rm -f k3d-k3d-cluster-serverlb k3d-k3d-cluster-agent-0 k3d-k3d-cluster-agent-1 k3d-k3d-cluster-server-0`  
→ Dann `chess-proxy` neu starten.

### game-service crasht / viele Restarts
Postgres braucht beim Start ein paar Sekunden. game-service wartet per `readinessProbe`.  
→ `docker exec k3d-eljachess-server-0 kubectl logs -n chess deployment/postgres`

### Cluster nach Server-Neustart weg
k3d-Container haben `--restart unless-stopped` — sie starten automatisch.  
Der `chess-proxy` Container ebenfalls.  
Falls doch weg: Cluster manuell neu starten mit `docker start k3d-eljachess-server-0 k3d-eljachess-agent-0 k3d-eljachess-agent-1 k3d-eljachess-serverlb`
