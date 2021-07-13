# V6 upgrade
SkyWalking v6 is widely used in many production environments. Follow the steps in the guide below to learn how to upgrade to a new release.

**NOTE**: The ways to upgrade are not limited to the steps below. 

## Use Canary Release
Like all applications, you may upgrade SkyWalking using the `canary release` method through the following steps.
1. Deploy a new cluster by using the latest version of SkyWalking OAP cluster with the new database cluster.
1. Once the target service (i.e. the service being monitored) has upgraded the agent.jar (or simply by rebooting), have `collector.backend_service`
pointing to the new OAP backend, and use/add a new namespace(`agent.namespace` in [Table of Agent Configuration Properties](../setup/service-agent/java-agent/README.md#table-of-agent-configuration-properties)).
The namespace will prevent conflicts from arising between different versions.
1. When all target services have been rebooted, the old OAP clusters could be discarded.

The `Canary Release` method works for any version upgrades.

## Online Hot Reboot Upgrade
The reason we require `Canary Release` is that the SkyWalking agent has cache mechanisms, and switching to a new cluster causes the 
cache to become unavailable for new OAP clusters.
In version 6.5.0+ (especially for agent versions), we have [**Agent hot reboot trigger mechanism**](../setup/backend/backend-setup.md#agent-hot-reboot-trigger-mechanism-in-oap-server-upgrade).
This streamlines the upgrade process as we **deploy a new cluster by using the latest version of SkyWalking OAP cluster with the new database cluster**,
and shift the traffic to the new cluster once and for all. Based on the mechanism, all agents will enter the `cool_down` mode, and come
back online. For more details, see the backend setup documentation.

**NOTE**: A known bug in 6.4.0 is that its agent may have re-connection issues; therefore, even though this bot reboot mechanism has been included in 6.4.0, it may not work under some network scenarios, especially in Kubernetes.

## Agent Compatibility
All versions of SkyWalking 6.x (and even 7.x) are compatible with each other, so users could simply upgrade the OAP servers. 
As the agent has also been enhanced in the latest versions, according to the SkyWalking team's recommendation, upgrade the agent as soon as practicable.
