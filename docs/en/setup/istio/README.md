# Work with Istio

Instructions for transport Istio's metrics to SkyWalking OAP server.

## Prerequisites

Istio should be installed in kubernetes cluster. Follow [Istio quick start](https://istio.io/docs/setup/kubernetes/quick-start/)
to finish it.

## Deploy Skywalking backend

Follow the [deploying backend in kubernetes](../backend/backend-k8s.md) to install oap server in kubernetes cluster.

## Setup Istio to send metrics to oap

1. Install Istio metric template

`kubectl apply -f https://raw.githubusercontent.com/istio/istio/1.3.3/mixer/template/metric/template.yaml`

2. Install SkyWalking adapter

`kubectl apply -f ./yaml/skywalkingadapter.yml`

Find the `skywalkingadapter.yml` at [here](yaml/skywalkingadapter.yml).
