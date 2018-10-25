# Work with Istio

Instructions for transport Istio's metrics to skywalking oap server.

## Prerequisites

Istio should be installed in kubernetes cluster. Follow [Istio quick start](https://istio.io/docs/setup/kubernetes/quick-start/)
to finish it.

## Deploy Skywalking OAP server

Follow the [deploying backend in kubernetes](../backend/backend-k8s.md) to install oap server in kubernetes cluster.

## Setup Istio to send metric to oap

Use `kubectl apply -f ` with the scripts in `kubernetes/istio` to setup.
