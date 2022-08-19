# On Demand Pod Logs

This feature is to fetch the Pod logs on users' demand, the logs are fetched and displayed in real time,
and are not persisted in any kind. This is helpful when users want to do some experiments and monitor the
logs and see what's happing inside the service.

Note: if you print secrets in the logs, they are also visible to the UI, so for the sake of security, this
feature is disabled by default, please read the configuration documentation to enable this feature manually.

## How it works

As the name indicates, this feature only works for Kubernetes Pods.

SkyWalking OAP collects and saves the service instance's namespace and Pod name in the service instance's
properties, named `namespace` and `pod`, users can select the same and UI should fetch the logs by service
instance in a given interval and display the logs in UI, OAP receives the query and checks the instance's
properties and use the `namespace` and `pod` to locate the Pod and query the logs.

If you want to register a service instance that has on demand logs available, you should add `namespace`
and `pod` in the service instance properties, so that you can query the real time logs from that Pod.

That said, in order to make this feature work properly, you should in advance configure the cluster role for
OAP to list/get namespaces, services, pods and pods/log.
