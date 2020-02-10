# V6 upgrade
SkyWalking v6 is widely used in many production environments. Users may wants to upgrade to an old release to new.
This is a guidance to tell users how to do that.

**NOTICE**, the following ways are not the only ways to do upgrade.

## Use Canary Release
Like all applications, SkyWalking could use `canary release` method to upgrade by following these steps
1. Deploy a new cluster by using the latest(or new) version of SkyWalking OAP cluster with new database cluster.
1. Once the target(being monitored) service has chance to upgrade the agent.jar(or just simply reboot), change the `collector.backend_service`
pointing to the new OAP backend, and use/add a new namespace(`agent.namespace` in [Table of Agent Configuration Properties](../setup/service-agent/java-agent/README.md#table-of-agent-configuration-properties)).
The namespace will avoid the conflict between different versions.
1. When all target services have been rebooted, the old OAP clusters could be discarded.

`Canary Release` methods works for any version upgrade.

## Online Hot Reboot Upgrade
The reason we required `Canary Release` is, SkyWalking agent has cache mechanisms, switching to a new cluster makes the 
cache unavailable for new OAP cluster.
In the 6.5.0+(especially for agent version), we have [**Agent hot reboot trigger mechanism**](../setup/backend/backend-setup.md#agent-hot-reboot-trigger-mechanism-in-oap-server-upgrade).
By using that, we could do upgrade an easier way, **deploy a new cluster by using the latest(or new) version of SkyWalking OAP cluster with new database cluster**,
and shift the traffic to the new cluster once for all. Based on the mechanism, all agents will go into `cool_down` mode, then
back online. More detail, read the backend setup document.

**NOTICE**, as a known bug in 6.4.0, its agent could have re-connection issue, so, even this bot reboot mechanism included in 6.4.0,
it may not work in some network scenarios, especially in k8s.

## Agent Compatibility
All versions of SkyWalking 6.x(even 7.x) are compatible with each others, so users could only upgrade the OAP servers first. 
The agent is also enhanced from version to version, so from SkyWalking team's recommendations, upgrade the agent once you have the chance.
