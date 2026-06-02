#!/bin/bash
set -e

echo "========================================="
echo "k3d Deployment Script"
echo "========================================="

# 1. Docker installieren (falls nicht vorhanden)
if ! command -v docker &> /dev/null; then
    echo "Installing Docker..."
    curl -fsSL https://get.docker.com -o get-docker.sh
    sudo sh get-docker.sh
    sudo usermod -aG docker $USER
    newgrp docker
else
    echo "✓ Docker already installed"
fi

# 2. k3d installieren
if ! command -v k3d &> /dev/null; then
    echo "Installing k3d..."
    curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
else
    echo "✓ k3d already installed"
fi

# 3. k3d Cluster erstellen
echo "Creating k3d cluster..."
k3d cluster create eljachess --agents 2 --ports "30080:30080@loadbalancer" --ports "30050:30050@loadbalancer" || echo "Cluster might already exist"

# 4. kubectl context setzen
echo "Setting kubectl context..."
k3d kubeconfig get eljachess > ~/.kube/config || mkdir -p ~/.kube && k3d kubeconfig get eljachess > ~/.kube/config

# 5. Repo klonen (falls nicht vorhanden)
if [ ! -d "EJ-Chess-Systems" ]; then
    echo "Cloning repository..."
    git clone https://github.com/EJ-Chess/EJ-Chess-Systems.git
fi

cd EJ-Chess-Systems

# 6. Kubernetes Manifeste anwenden
echo "Applying Kubernetes manifests..."
kubectl apply -f k8s/

# 7. Status prüfen
echo ""
echo "Waiting for pods to be ready..."
sleep 10
kubectl get pods -n chess-systems
kubectl get svc -n chess-systems

echo ""
echo "========================================="
echo "✓ k3d deployment complete!"
echo "Web-UI: http://localhost:30080"
echo "pgAdmin: http://localhost:30050"
echo "========================================="
