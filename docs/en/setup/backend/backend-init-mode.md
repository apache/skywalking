# Init mode
SkyWalking backend supports multiple storage implementors. Most of them could initialize the storage, 
such as Elastic Search, Database automatically when the backend startup in first place.

But there are some unexpected happens based on the storage, such as
`When create Elastic Search indexes concurrently, because of several backend instances startup at the same time.`,
there is a change, the APIs of Elastic Search would be blocked without any exception.
And this has more chances happen in container management platform, like k8s.

That is where you need **Init mode** startup.

## Solution
Only one single instance should run in **Init mode** before other instances start up.
And this instance will exit graciously after all initialization steps are done.

Use `oapServiceInit.sh`/`oapServiceInit.bat` to start up backend. You should see the following logs
> 2018-11-09 23:04:39,465 - org.apache.skywalking.oap.server.starter.OAPServerStartUp -2214 [main] INFO  [] - OAP starts up in init mode successfully, exit now...

## Kubernetes
Initialization in this mode would be included in our Kubernetes scripts and Helm.