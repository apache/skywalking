# On Demand Pod Logs

This feature is to fetch the Pod logs on users' demand, the logs are fetched and displayed in real time,
and are not persisted in any kind. This is helpful when users want to do some experiments and monitor the
logs and see what's happing inside the service.

Note: if you print secrets in the logs, they are also visible to the UI, so for the sake of security, this
feature is disabled by default, please read the configuration documentation to enable this feature manually.

## How it works

As the name indicates, this feature only works for Kubernetes Pods.

SkyWalking OAP lists the Kubernetes namespaces, services, Pods and containers in the UI for users to select,
users can select the same and UI should fetch the logs in a given interval and display the logs in UI.

That said, in order to make this feature work properly, you should in advance configure the cluster role for
OAP to list/get namespaces, services, pods and pods/log.
