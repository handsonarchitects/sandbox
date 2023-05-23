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
helm show chart apache/pulsar
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

## Update Pulsar
```bash
helm upgrade -f src/test/resources/values.yaml \
  local-pulsar \
  apache/pulsar \
  -n pulsar
```

## Uninstall Pulsar
```bash
helm uninstall local-pulsar -n pulsar
```