# Kubernetes Deployment Guide (Lokal)

## Überblick

Du hast eine **vollständige Kubernetes-Infrastruktur** mit **k3d** deployed. Das ist deine moderne Cloud-Native Infrastruktur.

## Was ist k3d?

- **k3d** = Lightweight Kubernetes in Docker
- Perfekt für Entwicklung & Testing
- Echte Kubernetes Features (Manifeste, Services, Volumes, Networking)
- Läuft in Docker Containern
- Viel leichter als full Kubernetes

## Deine Infrastruktur

### Services (5 Deployments)

| Service | Port | Rolle |
|---------|------|-------|
| **chess-ui** | 8000 | React Web-UI |
| **game-service** (chess-api) | 8081 | REST API |
| **bot-service** | (internal) | Bot Engine |
| **postgres** | 5432 | Database |
| **pgadmin** | (optional) | DB Admin Tool |

### Networking

```
Browser
  ↓ (SSH Tunnel: localhost:8000)
  ↓
Server 141.37.123.121
  ↓
k3d Cluster (eljachess)
  ├─ chess-ui (NodePort 30080:80)
  ├─ game-service (ClusterIP)
  ├─ bot-service (ClusterIP)
  ├─ postgres (ClusterIP + PersistentVolume)
  └─ pgadmin (NodePort 30050:80)
```

## Zugriff (von Zuhause über VPN)

### SSH Tunnel starten (auf deinem PC)

```bash
ssh -L 8000:localhost:8000 -L 8081:localhost:8081 chess@141.37.123.121 -N
```

Dann im Browser:
- **Web-UI**: http://localhost:8000
- **API**: http://localhost:8081/q/health

### Auf dem Server selbst

```bash
curl http://localhost:8000
curl http://localhost:8081/q/health
```

## Management Commands

### Status prüfen (auf dem Server)

```bash
# Alle Pods
~/.local/bin/kubectl get pods -n chess

# Services
~/.local/bin/kubectl get svc -n chess

# Logs
~/.local/bin/kubectl logs -n chess deployment/game-service
~/.local/bin/kubectl logs -n chess deployment/bot-service

# Port-forwards
ps aux | grep port-forward
```

### Cluster Management

```bash
# k3d Cluster Info
~/.local/bin/k3d cluster list
~/.local/bin/k3d cluster info eljachess

# Cluster stoppen
~/.local/bin/k3d cluster stop eljachess

# Cluster starten
~/.local/bin/k3d cluster start eljachess

# Cluster löschen (Vorsicht!)
~/.local/bin/k3d cluster delete eljachess
```

### Services updaten

Wenn du den Code änderst und neu builden möchtest:

```bash
# 1. Lokal bauen
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild

# 2. Images bauen
docker build -t eljachess/chess-api:latest modules/chess-api/
docker build -t eljachess/bot-service:latest modules/bot-service/

# 3. Auf Server transferieren
docker save eljachess/chess-api:latest eljachess/bot-service:latest | gzip | ssh chess@141.37.123.121 "gunzip | docker load"

# 4. Images in k3d importieren
ssh chess@141.37.123.121
~/.local/bin/k3d image import ~/images.tar -c eljachess

# 5. Pods neustarten
~/.local/bin/kubectl rollout restart deployment/game-service -n chess
~/.local/bin/kubectl rollout restart deployment/bot-service -n chess
```

## Kubernetes Manifeste (in `k8s/`)

```
k8s/
├── namespace.yaml           # Namespace "chess"
├── configmap.yaml          # Environment variables
├── secret.yaml             # Passwörter
├── postgres/               # Database
│   ├── deployment.yaml
│   ├── service.yaml
│   └── pvc.yaml           # Persistent Volume für Daten
├── game-service/           # Chess API
│   ├── deployment.yaml
│   └── service.yaml
├── bot-service/            # Bot Engine
│   ├── deployment.yaml
│   └── service.yaml
├── chess-ui/               # Web-UI
│   ├── deployment.yaml
│   └── service.yaml
└── pgadmin/                # DB Admin (optional)
    ├── deployment.yaml
    └── service.yaml
```

## Troubleshooting

### Pod startet nicht?
```bash
~/.local/bin/kubectl describe pod <pod-name> -n chess
~/.local/bin/kubectl logs <pod-name> -n chess
```

### Keine Verbindung zur Datenbank?
```bash
# Postgres läuft?
~/.local/bin/kubectl get pods -n chess | grep postgres

# PersistentVolume gebunden?
~/.local/bin/kubectl get pvc -n chess
```

### Port bereits in Benutzung?
```bash
ps aux | grep port-forward
pkill -f port-forward
```

### k3d Cluster abgestürzt?
```bash
~/.local/bin/k3d cluster list
~/.local/bin/k3d cluster start eljachess
~/.local/bin/kubectl get pods -n chess
```

## Wichtige Dateien

- **deployment-script**: `deploy-k3d.sh` (vollautomatisches Setup)
- **Kubernetes-Konfiguration**: `~/.kube/config` (auf dem Server)
- **Docker Images**: `~/images.tar` (auf dem Server)
- **kubectl**: `~/.local/bin/kubectl`
- **k3d**: `~/.local/bin/k3d`

## Sicherheit & Permissions

⚠️ **chess User hat keine sudo-Rechte!**
- Alle Tools sind in `~/.local/bin/` installiert
- k3d läuft ohne root
- Keine System-Wide Installationen

Falls etwas admin-Rechte braucht → Admin fragen

## Next Steps

1. ✅ Deployment funktioniert
2. ⏭️ Monitoring hinzufügen (Prometheus/Grafana)
3. ⏭️ Logging zentralisieren (ELK Stack)
4. ⏭️ Auto-Scaling konfigurieren
5. ⏭️ CI/CD Pipeline mit GitHub Actions

## Notizen

- **k3d** ist für Produktion nicht ideal (echtes Kubernetes verwenden)
- **pgadmin** crasht manchmal (optional, nicht kritisch)
- **SSH Tunnel** braucht du für VPN-Zugriff von zuhause
- **Alle Configs sind in Git** → reproducible infrastructure!
