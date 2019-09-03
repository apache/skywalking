# Work with Istio

Instructions for transport Istio's metrics to SkyWalking OAP server.

## Prerequisites

Istio should be installed in kubernetes cluster. Follow [Istio quick start](https://istio.io/docs/setup/kubernetes/quick-start/)
to finish it.

## Deploy Skywalking backend

Follow the [deploying backend in kubernetes](../backend/backend-k8s.md) to install oap server in kubernetes cluster.

## Setup Istio to send metrics to oap

The SkyWalking uses Istio bypass adapter collects metrics. Use `kubectl apply -f` with the `yaml`(s) in [the yaml folder](yaml) to setup.
 