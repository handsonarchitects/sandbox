# Pulsar Sandbox

## Requirements
- Docker
- Kubernetes
- [Helm](https://helm.sh/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)

## How to run
Install and init Apache Pulsar cluster:

```bash
helm repo add apache https://pulsar.apache.org/charts
docker pull apachepulsar/pulsar-all:3.0.0
helm install \
    -f src/test/resources/values.yaml \
    --namespace pulsar \
    local-pulsar apache/pulsar --create-namespace
```

Wait until all PODs are `running`:
```bash
watch -n 5 kubectl get pods --namespace pulsar
```

From the root directory run the unit tests:
```bash
./gradlew :pulsar:test --rerun-tasks
```

## How to clean up

```bash
helm uninstall \
    --namespace pulsar \
    local-pulsar
```