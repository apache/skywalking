# Kubernetes (K8s) monitoring

Kubernetes is an open-source container-orchestration system for automating computer application deployment, scaling, and
management. It was originally designed by Google and is now maintained by the Cloud Native Computing Foundation. It aims
to provide a "platform for automating deployment, scaling, and operations of application containers across clusters of
hosts". It works with a range of container tools, including Docker.

Nowadays, Kubernetes is the fundamental infrastructure for cloud native applications. SkyWalking provides the following
ways to monitor deployments on Kubernetes.

1. Use kube-state-metrics (KSM) and cAdvisor to collect metrics of Kubernetes resources, such as CPU, service, pod, and
   node. Read [kube-state-metrics and cAdvisor setup guide](./backend-k8s-monitoring-metrics-cadvisor.md) for more details.
2. Rover is a SkyWalking native eBPF agent to collect network Access Logs to support topology-aware and metrics 
   analysis. Meanwhile, due to the power of eBPF, it could profile running services written by C++, Rust, Golang, etc. 
   Read [Rover setup guide](./backend-k8s-monitoring-rover.md) for more details.
3. If Cilium is installed in Kubernetes, use Cilium Fetcher to collect network traffic data of services through Cilium Hubble APIs.
   This data can be used to create topology maps and to provide L4 and L7 layer metrics. 
   Read [Cilium Fetcher setup guide](./backend-k8s-monitoring-cilium.md) for more details.

SkyWalking deeply integrates with Kubernetes to help users understand the status of their applications on Kubernetes.
Cillium with Hubble is in our v10 plan. 