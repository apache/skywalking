# Observability Analysis Platform

SkyWalking OAP and UI provides dozens of features to support observability analysis for your services, cloud
infrastructure, open-source components, and more.

Besides those out-of-box features for monitoring, users could leverage the powerful and flexible analysis language to
build their own analysis and visualization.

There are 3 powerful and native language engines designed to analyze observability data from the above areas.

1. [Observability Analysis Language](oal.md) processes native traces and service mesh data to build metrics of entity
   and topology map.
1. [Meter Analysis Language](mal.md) is responsible for metrics calculation for native meter data, and adopts a stable
   and widely used metrics system, such as Prometheus and OpenTelemetry.
1. [Log Analysis Language](lal.md) focuses on analyzing log contents to format and label them, and extract metrics from
   them to feed Meter Analysis Language for further analysis.

SkyWalking community is willing to accept your monitoring extension powered by these languages, if the monitoring targets are
public and general usable.
