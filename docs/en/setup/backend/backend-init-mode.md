# Init mode
The SkyWalking backend supports multiple storage implementors. Most of them would automatically initialize the storage, 
such as Elastic Search or Database, when the backend starts up at first.

But there may be some unexpected events that may occur with the storage, such as
`When multiple Elastic Search indexes are created concurrently, these backend instances would start up at the same time.`,
When there is a change, the APIs of Elastic Search would be blocked without reporting any exception.
This often happens on container management platforms, such as k8s.

This is where you need the **Init mode** startup.

## Solution
Only one single instance should run in the **Init mode** before other instances start up.
And this instance will exit graciously after all initialization steps are done.

Use `oapServiceInit.sh`/`oapServiceInit.bat` to start up backend. You should see the following logs:
> 2018-11-09 23:04:39,465 - org.apache.skywalking.oap.server.starter.OAPServerStartUp -2214 [main] INFO  [] - OAP starts up in init mode successfully, exit now...

## Kubernetes
Initialization in this mode would be included in our Kubernetes scripts and Helm.
