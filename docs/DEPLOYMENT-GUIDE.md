# Deployment Guide: K3d + Traefik auf Uniserver

Dieses Guide erklärt, wie man K3d (Lightweight Kubernetes in Docker) mit einem Traefik Reverse Proxy auf einem Uniserver mit eingeschränkten Rechten deployed.

## Voraussetzungen

- Docker muss installiert und am Laufen sein
- SSH-Zugang zum Server
- Keine Root-Rechte nötig (Docker genügt)
- Internet-Zugang auf dem Server

## Schritt 1: Mit dem Server verbinden

```bash
ssh <user>@<server-adresse>
# z.B.: ssh chess@141.37.123.121
```

## Schritt 2: Verfügbare Ports herausfinden

Port 80 und 443 sind oft belegt. Test durchführen:

```bash
# Diese Ports müssen FREI sein:
nc -z -w1 127.0.0.1 8080  # HTTP-Alternative
nc -z -w1 127.0.0.1 8443  # HTTPS-Alternative
nc -z -w1 127.0.0.1 9090  # Dashboard-Alternative
```

Falls alle frei sind → weiter mit den Standard-Ports.
Falls belegt → Alternativen verwenden (z.B. 8080, 8443, 9090).

## Schritt 3: K3d installieren

```bash
curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
```

Prüfen:
```bash
k3d version
```

## Schritt 4: kubectl installieren

```bash
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/
```

Prüfen:
```bash
kubectl version --client
```

## Schritt 5: K3d Cluster starten

### Mit Standard-Ports (80, 443):
```bash
k3d cluster create k3d-cluster \
  --servers 1 \
  --agents 2 \
  -p "80:80@loadbalancer" \
  -p "443:443@loadbalancer" \
  --wait
```

### Mit alternativen Ports (8080, 8443):
```bash
k3d cluster create k3d-cluster \
  --servers 1 \
  --agents 2 \
  -p "8080:80@loadbalancer" \
  -p "8443:443@loadbalancer" \
  --wait
```

Prüfen:
```bash
kubectl get nodes
```

## Schritt 6: Traefik Reverse Proxy starten

Erstelle `docker-compose-final.yml`:

```bash
cat > docker-compose-final.yml << 'EOF'
version: '3.8'
services:
  traefik:
    image: traefik:v3.0
    container_name: traefik-proxy
    command:
      - "--api.insecure=true"
      - "--providers.docker=true"
      - "--providers.docker.exposedbydefault=false"
      - "--entrypoints.web.address=:8080"
      - "--entrypoints.websecure.address=:8443"
    ports:
      - "8080:8080"
      - "8443:8443"
      - "9090:8080"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
    networks:
      - traefik-network
    restart: unless-stopped
networks:
  traefik-network:
    driver: bridge
EOF
```

**Wichtig:** Falls du andere Ports nutzt, ändere die `address:` und `ports:` Zeilen entsprechend!

Starten:
```bash
docker compose -f docker-compose-final.yml up -d
```

Prüfen:
```bash
docker ps | grep traefik
```

## Schritt 7: Test-Service deployen

```bash
# Deployment erstellen
kubectl create deployment hello-app --image=nginx:latest

# Service exponieren (LoadBalancer)
kubectl expose deployment hello-app --port=80 --type=LoadBalancer
```

Prüfen:
```bash
kubectl get all
```

## URLs zum Zugriff

| Komponente | URL |
|---|---|
| Traefik Dashboard | http://\<server-ip\>:9090 |
| Test Service | http://\<server-ip\>:8080 |

## Troubleshooting

### Problem: Traefik Container restartet dauernd

Logs anschauen:
```bash
docker logs traefik-proxy
```

Häufige Fehler:
- **Port already allocated**: Ein Port ist schon belegt. Andere Ports wählen.
- **YAML-Fehler**: Indentierung in docker-compose-final.yml checken (2 Spaces pro Level!)

### Problem: kubectl funktioniert nicht

Kubeconfig setzen:
```bash
export KUBECONFIG=$(k3d kubeconfig write k3d-cluster)
```

### Problem: K3d Cluster antwortet nicht

Cluster neu starten:
```bash
k3d cluster stop k3d-cluster
k3d cluster start k3d-cluster
```

### Logs ansehen

Traefik:
```bash
docker logs -f traefik-proxy
```

K3d Cluster:
```bash
kubectl logs -n kube-system deployment/coredns
```

## Eigene Services deployen

Beispiel `my-service.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app
spec:
  replicas: 2
  selector:
    matchLabels:
      app: my-app
  template:
    metadata:
      labels:
        app: my-app
    spec:
      containers:
      - name: app
        image: <dein-image>:latest
        ports:
        - containerPort: 8000
---
apiVersion: v1
kind: Service
metadata:
  name: my-app-service
spec:
  selector:
    app: my-app
  ports:
  - port: 80
    targetPort: 8000
  type: LoadBalancer
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: my-app-ingress
  annotations:
    traefik.ingress.kubernetes.io/router.entrypoints: web
spec:
  rules:
  - host: myapp.localhost
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: my-app-service
            port:
              number: 80
```

Deployen:
```bash
kubectl apply -f my-service.yaml
```

## Nützliche Befehle

```bash
# Cluster-Status
k3d cluster list
kubectl cluster-info

# Alle Ressourcen anzeigen
kubectl get all

# Logs anschauen
kubectl logs deployment/<name>
kubectl logs -f pod/<name>

# In einen Container gehen
kubectl exec -it pod/<name> -- /bin/bash

# Port-Forwarding
kubectl port-forward service/<name> 8000:80

# Deployment neu starten
kubectl rollout restart deployment/<name>

# Ressourcen löschen
kubectl delete deployment <name>
kubectl delete service <name>

# Cluster stoppen/starten
k3d cluster stop k3d-cluster
k3d cluster start k3d-cluster

# Docker-Compose Logs
docker-compose -f docker-compose-final.yml logs -f
```

## Sicherheit & Hinweise

⚠️ **WICHTIG:**
- `--api.insecure=true` in Traefik ist nur für Development geeignet
- Für Production: SSL-Zertifikate mit Let's Encrypt einrichten
- K3d ist nicht für Production geeignet - nur für Development/Testing

## Weitere Ressourcen

- K3d Docs: https://k3d.io/
- Kubernetes Docs: https://kubernetes.io/docs/
- Traefik Docs: https://doc.traefik.io/traefik/
