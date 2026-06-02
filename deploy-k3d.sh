#!/bin/bash
set -e

echo "========================================="
echo "k3d Deployment Script (No Sudo Required)"
echo "========================================="

# 1. Sicherstellen, dass ~/.local/bin existiert
mkdir -p ~/.local/bin
export PATH="$HOME/.local/bin:$PATH"
echo "export PATH=\"\$HOME/.local/bin:\$PATH\"" >> ~/.bashrc

# 2. Docker in der Gruppe
if ! groups $USER | grep -q docker; then
    echo "WARNING: You might not be in docker group. Ask admin to add you: sudo usermod -aG docker $USER"
    echo "For now, using docker anyway..."
fi

# 3. k3d direkt downloaden (keine Installation)
if ! command -v k3d &> /dev/null; then
    echo "Downloading k3d binary..."
    VERSION=$(curl -s https://api.github.com/repos/k3d-io/k3d/releases/latest | grep tag_name | cut -d '"' -f 4 | sed 's/^v//')
    curl -L https://github.com/k3d-io/k3d/releases/download/v${VERSION}/k3d-linux-amd64 -o ~/.local/bin/k3d
    chmod +x ~/.local/bin/k3d
    echo "✓ k3d installed to ~/.local/bin/k3d"
else
    echo "✓ k3d already installed"
fi

# 4. k3d Cluster erstellen
echo "Creating k3d cluster 'eljachess'..."
k3d cluster create eljachess \
    --agents 2 \
    --ports "30080:30080@loadbalancer" \
    --ports "30050:30050@loadbalancer" \
    || echo "Cluster might already exist, continuing..."

# 5. kubeconfig setzen
echo "Setting kubeconfig..."
mkdir -p ~/.kube
k3d kubeconfig get eljachess > ~/.kube/config

# 6. Repository klonen (falls nicht vorhanden)
if [ ! -d "EJ-Chess-Systems" ]; then
    echo "Cloning repository..."
    git clone https://github.com/EJ-Chess/EJ-Chess-Systems.git
fi

cd EJ-Chess-Systems || exit 1

# 7. Docker Images importieren (falls noch nicht geschehen)
echo "Loading Docker images into k3d..."
docker save eljachess/chess-api:latest eljachess/bot-service:latest eljachess/chess-ui:latest 2>/dev/null | k3d image import - --cluster eljachess || echo "Images might already be loaded"

# 8. Kubernetes Manifeste anwenden
echo "Applying Kubernetes manifests..."
kubectl apply -f k8s/

# 9. Status prüfen
echo ""
echo "Waiting for pods to be ready (this may take 1-2 minutes)..."
sleep 15
kubectl get pods -n chess-systems
kubectl get svc -n chess-systems

echo ""
echo "========================================="
echo "✓ k3d deployment complete!"
echo "Web-UI: http://localhost:30080"
echo "pgAdmin: http://localhost:30050"
echo "========================================="
