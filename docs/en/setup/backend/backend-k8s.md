# Deploy backend in kubernetes

To install and configure skywalking in a Kubernetes cluster, follow these instructions.

## Prerequisites

Please promise the `skywalking` namespace existed in the cluster, otherwise, create a new one.

`kubctl apply -f kubernetes/namespace.yml`

## Deploy Elasticsearch

Use `kubectl apply -f ` with the scripts in `kubernetes/elasticsearch` to deploy elasticsearch servers
in the cluster.

> `01-storageclass.yml` assume to use GKE as the kubernetes provisioner. You could fix it according
to your kubernetes environment.

## Deploy OAP server 

Use `kubectl apply -f ` with the scripts in `kubernetes/opa` to deploy oap server
in the cluster.
