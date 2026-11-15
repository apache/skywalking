# Parameters for the Helm chart

The content of this document describes the parameters that can be configured in the Helm chart for SkyWalking BanyanDB. It's generated from the `values.yaml` file in the chart repository by [bitnami/readme-generator-for-helm](https://github.com/bitnami/readme-generator-for-helm)

## Parameters

### Common configuration

| Name               | Description                         | Value      |
| ------------------ | ----------------------------------- | ---------- |
| `fullnameOverride` | Override the full name of the chart | `""`       |
| `nameOverride`     | Override the name of the chart      | `banyandb` |

### Container image configuration

| Name               | Description                                                                                                                                 | Value                                  |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------- |
| `image.repository` | Docker repository for SkyWalking BanyanDB                                                                                                   | `docker.io/apache/skywalking-banyandb` |
| `image.tag`        | Image tag/version. REQUIRED, deliberately no default — every install must set it explicitly; templates fail fast via `required` when empty. | `""`                                   |
| `image.pullPolicy` | Image pull policy (e.g. IfNotPresent)                                                                                                       | `IfNotPresent`                         |

### Trace-pipeline sampler plugin configuration

| Name                              | Description                                                            | Value           |
| --------------------------------- | ---------------------------------------------------------------------- | --------------- |
| `plugins.enabled`                 | Enable trace-pipeline native sampler plugins on data nodes.            | `false`         |
| `plugins.image.repository`        | Docker repository for the plugin carrier image.                        | `""`            |
| `plugins.image.tag`               | Carrier image tag (empty defaults to <main image tag>-plugins-carrier) | `""`            |
| `plugins.image.pullPolicy`        | Carrier image pull policy                                              | `IfNotPresent`  |
| `plugins.mountMode`               | How to deliver plugins into the data-node pod:                         | `initContainer` |
| `plugins.initContainer.resources` | Resource requests/limits for the carrier copy init container           | `{}`            |
| `plugins.thirdParty.repository`   | Docker repository for third-party plugins                              | `""`            |
| `plugins.thirdParty.tag`          | Third-party plugin image tag                                           | `""`            |
| `plugins.thirdParty.pullPolicy`   | Third-party plugin image pull policy                                   | `IfNotPresent`  |

### Authentication configuration for BanyanDB

| Name                      | Description                                              | Value              |
| ------------------------- | -------------------------------------------------------- | ------------------ |
| `auth.enabled`            | Enable basic authentication (boolean)                    | `false`            |
| `auth.existingSecret`     | Use an existing Secret for credentials                   | `""`               |
| `auth.credentialsFileKey` | Key name in the Secret that stores the                   | `credentials.yaml` |
| `auth.users`              | List of users to configure when not using existingSecret | `[]`               |

### Canopy web console configuration

| Name                                                 | Description                                                                                                                                                                      | Value           |
| ---------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------- |
| `canopy.enabled`                                     | Deploy the canopy console (Deployment + Service).                                                                                                                                | `false`         |
| `canopy.image.repository`                            | Canopy image repository. Empty defaults to image.repository (the console ships in the same repo with a "-canopy" tag suffix).                                                    | `""`            |
| `canopy.image.tag`                                   | Canopy image tag. Empty defaults to "<image.tag>-canopy" (lockstep with the BanyanDB image, mirroring the plugins carrier pattern).                                              | `""`            |
| `canopy.image.pullPolicy`                            | Canopy image pull policy. Empty defaults to image.pullPolicy.                                                                                                                    | `""`            |
| `canopy.replicas`                                    | Number of canopy replicas. Should remain 1 because Canopy stores user sessions in process memory with sliding expiry (multi-replica requires sticky session routing at Ingress). | `1`             |
| `canopy.env`                                         | Extra environment variables appended to the canopy container                                                                                                                     | `[]`            |
| `canopy.auth.backend`                                | Console authentication backend: "local" (default) or "sso".                                                                                                                       | `local`         |
| `canopy.auth.username`                               | Default admin username (generated user only in local mode)                                                                                                                        | `admin`         |
| `canopy.auth.password`                               | Default admin password. Empty generates a strong random one on install (reused on upgrades via the generated Secret).                                                            | `""`            |
| `canopy.auth.sessionSecret`                          | Session signing secret (>= 32 chars). Empty generates a random one (also reused on upgrades).                                                                                    | `""`            |
| `canopy.auth.users`                                  | Extra console users appended after the default admin, each {username, passwordHash (bcrypt), role (admin|readonly)}                                                              | `[]`            |
| `canopy.auth.existingSecret`                         | Use an existing Secret instead of the generated one. Must contain keys "session-secret" and "users.yaml" (canopy users file format); may also carry "password" for NOTES output. | `""`            |
| `canopy.auth.sso.providers`                          | List of OIDC/OAuth2 identity providers {id, name, type, kind, displayName, issuer, clientId, clientSecretFile, clientSecretSecretRef, redirectUri, scopes, allowedDomains, roles}  | `[]`            |
| `canopy.auth.sso.roles.default`                      | Default assigned role for SSO users when no mapping rule matches ("viewer" or "readonly")                                                                                         | `readonly`      |
| `canopy.auth.sso.roles.mappings`                     | Explicit role mappings based on claims or groups                                                                                                                                 | `[]`            |
| `canopy.auth.breakGlass.enabled`                     | Enable break-glass emergency login mechanism (active only when primary SSO health probe reports unhealthy)                                                                       | `false`         |
| `canopy.auth.breakGlass.users`                       | List of emergency local users, each {username, passwordHash (argon2id|bcrypt), role (admin|readonly)}                                                                            | `[]`            |
| `canopy.auth.breakGlass.existingSecret`              | Reference an existing Secret containing break-glass configuration                                                                                                                | `""`            |
| `canopy.auth.existingAuthConfigSecret`               | Use an existing Kubernetes Secret containing "auth.yaml" instead of the chart-rendered auth configuration                                                                        | `""`            |
| `canopy.service.labels`                              | Labels for the canopy service                                                                                                                                                    | `{}`            |
| `canopy.service.annotations`                         | Annotations for the canopy service                                                                                                                                               | `{}`            |
| `canopy.service.port`                                | Canopy service port                                                                                                                                                              | `4000`          |
| `canopy.service.type`                                | Canopy service type                                                                                                                                                              | `ClusterIP`     |
| `canopy.service.externalIPs`                         | External IPs for the canopy service                                                                                                                                              | `[]`            |
| `canopy.service.loadBalancerIP`                      | Load balancer IP for the canopy service                                                                                                                                          | `""`            |
| `canopy.service.loadBalancerSourceRanges`            | Load balancer source ranges                                                                                                                                                      | `[]`            |
| `canopy.updateStrategy.type`                         | Update strategy type for canopy pods                                                                                                                                             | `RollingUpdate` |
| `canopy.updateStrategy.rollingUpdate.maxUnavailable` | Maximum unavailable pods for canopy update                                                                                                                                       | `1`             |
| `canopy.updateStrategy.rollingUpdate.maxSurge`       | Maximum surge pods for canopy update                                                                                                                                             | `1`             |
| `canopy.podAnnotations`                              | Pod annotations for canopy                                                                                                                                                       | `{}`            |
| `canopy.securityContext`                             | Security context for canopy pods                                                                                                                                                 | `{}`            |
| `canopy.priorityClassName`                           | Priority class name for canopy pods                                                                                                                                              | `""`            |
| `canopy.podDisruptionBudget`                         | Pod disruption budget for canopy                                                                                                                                                 | `{}`            |
| `canopy.resources`                                   | Resource requests/limits for canopy pods                                                                                                                                         | `{}`            |
| `canopy.tolerations`                                 | Tolerations for canopy pods                                                                                                                                                      | `[]`            |
| `canopy.nodeSelector`                                | Node selector for canopy pods                                                                                                                                                    | `[]`            |
| `canopy.affinity`                                    | Affinity rules for canopy pods                                                                                                                                                   | `{}`            |
| `canopy.ingress.enabled`                             | Create an Ingress for the canopy console. Canopy proxies /api and /monitoring, so this single ingress exposes both the console UI and the BanyanDB HTTP API.                     | `false`         |
| `canopy.ingress.labels`                              | Labels for the canopy ingress                                                                                                                                                    | `{}`            |
| `canopy.ingress.annotations`                         | Annotations for the canopy ingress                                                                                                                                               | `{}`            |
| `canopy.ingress.tls`                                 | TLS configuration for the canopy ingress                                                                                                                                         | `[]`            |
| `canopy.ingress.rules`                               | Ingress rules; each path's backend defaults to the canopy service when serviceName/port are omitted.                                                                             | `[]`            |

### Configuration for standalone deployment

| Name                                            | Description                                             | Value          |
| ----------------------------------------------- | ------------------------------------------------------- | -------------- |
| `standalone.enabled`                            | Enable standalone mode (boolean)                        | `false`        |
| `standalone.podAnnotations`                     | Additional pod annotations                              | `{}`           |
| `standalone.securityContext`                    | Security context for the pod                            | `{}`           |
| `standalone.containerSecurityContext`           | Container-level security context                        | `{}`           |
| `standalone.tls`                                | TLS configuration for the standalone pod                | `{}`           |
| `standalone.volumePermissions.enabled`          | Enable volume permissions init container                | `false`        |
| `standalone.volumePermissions.chownUser`        | User ID to chown the mounted volumes                    | `1000`         |
| `standalone.volumePermissions.chownGroup`       | Group ID to chown the mounted volumes                   | `1000`         |
| `standalone.volumePermissions.image`            | Image for the volume permissions init container         | `busybox:1.36` |
| `standalone.env`                                | Environment variables for the pod                       | `[]`           |
| `standalone.priorityClassName`                  | Priority class name for the pod                         | `""`           |
| `standalone.podDisruptionBudget`                | Pod disruption budget configuration                     | `{}`           |
| `standalone.tolerations`                        | Tolerations for pod scheduling                          | `[]`           |
| `standalone.nodeSelector`                       | Node selector for pod scheduling                        | `[]`           |
| `standalone.affinity`                           | Affinity rules for pod scheduling                       | `{}`           |
| `standalone.resources`                          | Resource requests/limits for the pod                    | `{}`           |
| `standalone.grpcSvc.labels`                     | Labels for GRPC service                                 | `{}`           |
| `standalone.grpcSvc.annotations`                | Annotations for GRPC service                            | `{}`           |
| `standalone.grpcSvc.port`                       | Port number for GRPC service                            | `17912`        |
| `standalone.httpSvc.labels`                     | Labels for HTTP service                                 | `{}`           |
| `standalone.httpSvc.annotations`                | Annotations for HTTP service                            | `{}`           |
| `standalone.httpSvc.port`                       | Port number for HTTP service                            | `17913`        |
| `standalone.httpSvc.type`                       | Service type (e.g., LoadBalancer)                       | `LoadBalancer` |
| `standalone.httpSvc.externalIPs`                | External IP addresses for the service                   | `[]`           |
| `standalone.httpSvc.loadBalancerIP`             | Load balancer IP address                                | `nil`          |
| `standalone.httpSvc.loadBalancerSourceRanges`   | Allowed source ranges for the load balancer             | `[]`           |
| `standalone.ingress.enabled`                    | Enable ingress (boolean)                                | `false`        |
| `standalone.ingress.labels`                     | Labels for ingress                                      | `{}`           |
| `standalone.ingress.annotations`                | Annotations for ingress                                 | `{}`           |
| `standalone.ingress.rules`                      | Ingress routing rules                                   | `[]`           |
| `standalone.ingress.tls`                        | TLS configuration for ingress                           | `[]`           |
| `standalone.sidecar`                            | Sidecar container configurations                        | `[]`           |
| `standalone.livenessProbe.initialDelaySeconds`  | Initial delay for liveness probe                        | `20`           |
| `standalone.livenessProbe.periodSeconds`        | Probe period in seconds                                 | `30`           |
| `standalone.livenessProbe.timeoutSeconds`       | Probe timeout in seconds                                | `5`            |
| `standalone.livenessProbe.successThreshold`     | Number of successful probes                             | `1`            |
| `standalone.livenessProbe.failureThreshold`     | Number of failed probes                                 | `5`            |
| `standalone.readinessProbe.initialDelaySeconds` | Initial delay for readiness probe                       | `20`           |
| `standalone.readinessProbe.periodSeconds`       | Probe period for readiness probe                        | `30`           |
| `standalone.readinessProbe.timeoutSeconds`      | Timeout in seconds for readiness probe                  | `5`            |
| `standalone.readinessProbe.successThreshold`    | Number of successful readiness probes                   | `1`            |
| `standalone.readinessProbe.failureThreshold`    | Number of failed readiness probes before marked unready | `5`            |
| `standalone.startupProbe.initialDelaySeconds`   | Initial delay for startup probe                         | `0`            |
| `standalone.startupProbe.periodSeconds`         | Probe period for startup probe                          | `10`           |
| `standalone.startupProbe.timeoutSeconds`        | Timeout in seconds for startup probe                    | `5`            |
| `standalone.startupProbe.successThreshold`      | Number of successful startup probes                     | `1`            |
| `standalone.startupProbe.failureThreshold`      | Number of failed startup probes before timeout          | `60`           |

### Cluster mode configuration

| Name              | Description                   | Value  |
| ----------------- | ----------------------------- | ------ |
| `cluster.enabled` | Enable cluster mode (boolean) | `true` |

### Schema Storage Configuration

| Name                         | Description                                        | Value      |
| ---------------------------- | -------------------------------------------------- | ---------- |
| `cluster.schemaStorage.mode` | Schema storage mode (only "property" is supported) | `property` |

### Property Mode Configuration

| Name                                                  | Description                                                  | Value        |
| ----------------------------------------------------- | ------------------------------------------------------------ | ------------ |
| `cluster.schemaStorage.property.serverRepairCron`     | Cron schedule for metadata property server repair trigger    | `@every 10m` |
| `cluster.schemaStorage.property.clientSyncInterval`   | Interval for property schema client synchronization          | `30s`        |
| `cluster.schemaStorage.property.clientMaxRecvMsgSize` | Maximum gRPC receive message size for property schema client | `""`         |
| `cluster.schemaStorage.property.tls.secretName`       | K8s Secret for schema client TLS (CA cert)                   | `""`         |

### Property Schema Server Configuration (data nodes only)

| Name                                                             | Description                                         | Value       |
| ---------------------------------------------------------------- | --------------------------------------------------- | ----------- |
| `cluster.schemaStorage.property.server.grpcHost`                 | Schema server gRPC listen address                   | `""`        |
| `cluster.schemaStorage.property.server.grpcPort`                 | Schema server gRPC port                             | `17916`     |
| `cluster.schemaStorage.property.server.flushTimeout`             | Interval for flushing in-memory data to disk        | `5s`        |
| `cluster.schemaStorage.property.server.expireDeleteTimeout`      | Soft-delete expiration timeout                      | `168h`      |
| `cluster.schemaStorage.property.server.maxRecvMsgSize`           | Maximum gRPC receive message size for schema server | `""`        |
| `cluster.schemaStorage.property.server.repairTreeSlotCount`      | Number of repair tree slots                         | `""`        |
| `cluster.schemaStorage.property.server.repairBuildTreeCron`      | Cron expression for repair tree build               | `@every 1h` |
| `cluster.schemaStorage.property.server.repairQuickBuildTreeTime` | Duration for quick tree build after startup         | `10m`       |
| `cluster.schemaStorage.property.server.maxFileSnapshotNum`       | Maximum number of file snapshots                    | `""`        |
| `cluster.schemaStorage.property.server.minFileSnapshotAge`       | Minimum age before a snapshot can be removed        | `1h`        |

### Property Schema Server TLS Configuration (data nodes only)

| Name                                                   | Description                                   | Value |
| ------------------------------------------------------ | --------------------------------------------- | ----- |
| `cluster.schemaStorage.property.server.tls.secretName` | K8s Secret for schema server TLS (cert + key) | `""`  |

### Property Schema Client TLS Configuration (data + liaison nodes)


### Node Discovery Configuration for Service Discovery

| Name                         | Description                     | Value |
| ---------------------------- | ------------------------------- | ----- |
| `cluster.nodeDiscovery.mode` | Node discovery mode (dns, file) | `dns` |

### DNS Mode Configuration

| Name                                          | Description                          | Value |
| --------------------------------------------- | ------------------------------------ | ----- |
| `cluster.nodeDiscovery.dns.fetchInitInterval` | Query interval during initialization | `5s`  |
| `cluster.nodeDiscovery.dns.fetchInitDuration` | Duration of initialization phase     | `5m`  |
| `cluster.nodeDiscovery.dns.fetchInterval`     | Query interval after initialization  | `15s` |
| `cluster.nodeDiscovery.dns.grpcTimeout`       | Timeout for gRPC metadata fetch      | `5s`  |

### File Mode Configuration

| Name                                              | Description                                                       | Value |
| ------------------------------------------------- | ----------------------------------------------------------------- | ----- |
| `cluster.nodeDiscovery.file.grpcTimeout`          | Timeout for metadata fetches over gRPC while using file discovery | `5s`  |
| `cluster.nodeDiscovery.file.fetchInterval`        | Interval to poll and reload the discovery file                    | `5m`  |
| `cluster.nodeDiscovery.file.retryInitialInterval` | Initial retry interval for failed node fetches metadata           | `1s`  |
| `cluster.nodeDiscovery.file.retryMaxInterval`     | Maximum retry interval for failed node fetches metadata           | `2m`  |
| `cluster.nodeDiscovery.file.retryMultiplier`      | Backoff multiplier applied between retries fetches metadata       | `2`   |

### Discovery file ConfigMap settings

| Name                                                | Description                                                         | Value        |
| --------------------------------------------------- | ------------------------------------------------------------------- | ------------ |
| `cluster.nodeDiscovery.file.configMap.existingName` | Existing ConfigMap name to mount as discovery file                  | `""`         |
| `cluster.nodeDiscovery.file.configMap.key`          | Data key inside the ConfigMap that stores the discovery file        | `nodes.yaml` |
| `cluster.nodeDiscovery.file.configMap.content`      | Inline YAML used to create the ConfigMap when existingName is empty | `""`         |

### Configuration for liaison component

| Name                                                          | Description                                                 | Value           |
| ------------------------------------------------------------- | ----------------------------------------------------------- | --------------- |
| `cluster.liaison.replicas`                                    | Number of liaison replicas                                  | `2`             |
| `cluster.liaison.podAnnotations`                              | Pod annotations for liaison                                 | `{}`            |
| `cluster.liaison.securityContext`                             | Security context for liaison pods                           | `{}`            |
| `cluster.liaison.containerSecurityContext`                    | Container-level security context for liaison                | `{}`            |
| `cluster.liaison.volumePermissions.enabled`                   | Enable volume permissions init container for liaison        | `false`         |
| `cluster.liaison.volumePermissions.chownUser`                 | User ID to chown the mounted volumes for liaison            | `1000`          |
| `cluster.liaison.volumePermissions.chownGroup`                | Group ID to chown the mounted volumes for liaison           | `1000`          |
| `cluster.liaison.volumePermissions.image`                     | Image for the volume permissions init container for liaison | `busybox:1.36`  |
| `cluster.liaison.env`                                         | Environment variables for liaison pods                      | `[]`            |
| `cluster.liaison.priorityClassName`                           | Priority class name for liaison pods                        | `""`            |
| `cluster.liaison.updateStrategy.type`                         | Update strategy type for liaison pods                       | `RollingUpdate` |
| `cluster.liaison.updateStrategy.rollingUpdate.maxUnavailable` | Maximum unavailable pods during update                      | `1`             |
| `cluster.liaison.podManagementPolicy`                         | Pod management policy for liaison StatefulSet               | `Parallel`      |
| `cluster.liaison.podDisruptionBudget`                         | Pod disruption budget for liaison                           | `{}`            |
| `cluster.liaison.tolerations`                                 | Tolerations for liaison pods                                | `[]`            |
| `cluster.liaison.nodeSelector`                                | Node selector for liaison pods                              | `[]`            |
| `cluster.liaison.affinity`                                    | Affinity rules for liaison pods                             | `{}`            |
| `cluster.liaison.podAffinityPreset`                           | Pod affinity preset for liaison                             | `""`            |
| `cluster.liaison.podAntiAffinityPreset`                       | Pod anti-affinity preset for liaison                        | `soft`          |
| `cluster.liaison.resources.requests`                          | Resource requests for liaison pods                          | `[]`            |
| `cluster.liaison.resources.limits`                            | Resource limits for liaison pods                            | `[]`            |
| `cluster.liaison.grpcSvc.labels`                              | Labels for GRPC service for liaison                         | `{}`            |
| `cluster.liaison.grpcSvc.annotations`                         | Annotations for GRPC service for liaison                    | `{}`            |
| `cluster.liaison.grpcSvc.port`                                | Port number for GRPC service for liaison                    | `17912`         |
| `cluster.liaison.sidecar`                                     | Sidecar containers for liaison pods                         | `[]`            |
| `cluster.liaison.httpSvc.labels`                              | Labels for HTTP service for liaison                         | `{}`            |
| `cluster.liaison.httpSvc.annotations`                         | Annotations for HTTP service for liaison                    | `{}`            |
| `cluster.liaison.httpSvc.port`                                | Port number for HTTP service for liaison                    | `17913`         |
| `cluster.liaison.httpSvc.type`                                | Service type for HTTP service for liaison                   | `LoadBalancer`  |
| `cluster.liaison.httpSvc.externalIPs`                         | External IP addresses for liaison HTTP service              | `[]`            |
| `cluster.liaison.httpSvc.loadBalancerIP`                      | Load balancer IP for liaison HTTP service                   | `nil`           |
| `cluster.liaison.httpSvc.loadBalancerSourceRanges`            | Allowed source ranges for liaison HTTP service              | `[]`            |
| `cluster.liaison.ingress.enabled`                             | Enable ingress for liaison                                  | `false`         |
| `cluster.liaison.ingress.labels`                              | Labels for ingress of liaison                               | `{}`            |
| `cluster.liaison.ingress.annotations`                         | Annotations for ingress of liaison                          | `{}`            |
| `cluster.liaison.ingress.rules`                               | Ingress rules for liaison                                   | `[]`            |
| `cluster.liaison.ingress.tls`                                 | TLS configuration for liaison ingress                       | `[]`            |
| `cluster.liaison.livenessProbe.initialDelaySeconds`           | Initial delay for liaison liveness probe                    | `20`            |
| `cluster.liaison.livenessProbe.periodSeconds`                 | Probe period for liaison liveness probe                     | `30`            |
| `cluster.liaison.livenessProbe.timeoutSeconds`                | Timeout in seconds for liaison liveness probe               | `5`             |
| `cluster.liaison.livenessProbe.successThreshold`              | Success threshold for liaison liveness probe                | `1`             |
| `cluster.liaison.livenessProbe.failureThreshold`              | Failure threshold for liaison liveness probe                | `5`             |
| `cluster.liaison.readinessProbe.initialDelaySeconds`          | Initial delay for liaison readiness probe                   | `20`            |
| `cluster.liaison.readinessProbe.periodSeconds`                | Probe period for liaison readiness probe                    | `30`            |
| `cluster.liaison.readinessProbe.timeoutSeconds`               | Timeout in seconds for liaison readiness probe              | `5`             |
| `cluster.liaison.readinessProbe.successThreshold`             | Success threshold for liaison readiness probe               | `1`             |
| `cluster.liaison.readinessProbe.failureThreshold`             | Failure threshold for liaison readiness probe               | `5`             |
| `cluster.liaison.startupProbe.initialDelaySeconds`            | Initial delay for liaison startup probe                     | `0`             |
| `cluster.liaison.startupProbe.periodSeconds`                  | Probe period for liaison startup probe                      | `10`            |
| `cluster.liaison.startupProbe.timeoutSeconds`                 | Timeout in seconds for liaison startup probe                | `5`             |
| `cluster.liaison.startupProbe.successThreshold`               | Success threshold for liaison startup probe                 | `1`             |
| `cluster.liaison.startupProbe.failureThreshold`               | Failure threshold for liaison startup probe                 | `60`            |

### Configuration for data component

| Name                                                           | Description                                                                     | Value                                        |
| -------------------------------------------------------------- | ------------------------------------------------------------------------------- | -------------------------------------------- |
| `cluster.data.nodeTemplate.replicas`                           | Number of data replicas by default                                              | `2`                                          |
| `cluster.data.nodeTemplate.podAnnotations`                     | Pod annotations for data pods                                                   | `{}`                                         |
| `cluster.data.nodeTemplate.securityContext`                    | Security context for data pods                                                  | `{}`                                         |
| `cluster.data.nodeTemplate.containerSecurityContext`           | Container-level security context for data pods                                  | `{}`                                         |
| `cluster.data.nodeTemplate.volumePermissions.enabled`          | Enable volume permissions init container for data pods                          | `false`                                      |
| `cluster.data.nodeTemplate.volumePermissions.chownUser`        | User ID to chown the mounted volumes for data pods                              | `1000`                                       |
| `cluster.data.nodeTemplate.volumePermissions.chownGroup`       | Group ID to chown the mounted volumes for data pods                             | `1000`                                       |
| `cluster.data.nodeTemplate.volumePermissions.image`            | Image for the volume permissions init container for data pods                   | `busybox:1.36`                               |
| `cluster.data.nodeTemplate.env`                                | Environment variables for data pods                                             | `[]`                                         |
| `cluster.data.nodeTemplate.priorityClassName`                  | Priority class name for data pods                                               | `""`                                         |
| `cluster.data.nodeTemplate.podDisruptionBudget.maxUnavailable` | Maximum unavailable data pods                                                   | `1`                                          |
| `cluster.data.nodeTemplate.tolerations`                        | Tolerations for data pods                                                       | `[]`                                         |
| `cluster.data.nodeTemplate.nodeSelector`                       | Node selector for data pods                                                     | `[]`                                         |
| `cluster.data.nodeTemplate.affinity`                           | Affinity rules for data pods                                                    | `{}`                                         |
| `cluster.data.nodeTemplate.podAffinityPreset`                  | Pod affinity preset for data pods                                               | `""`                                         |
| `cluster.data.nodeTemplate.podAntiAffinityPreset`              | Pod anti-affinity preset for data pods                                          | `soft`                                       |
| `cluster.data.nodeTemplate.resources.requests`                 | Resource requests for data pods                                                 | `[]`                                         |
| `cluster.data.nodeTemplate.resources.limits`                   | Resource limits for data pods                                                   | `[]`                                         |
| `cluster.data.nodeTemplate.grpcSvc.labels`                     | Labels for GRPC service for data pods                                           | `{}`                                         |
| `cluster.data.nodeTemplate.grpcSvc.annotations`                | Annotations for GRPC service for data pods                                      | `{}`                                         |
| `cluster.data.nodeTemplate.grpcSvc.port`                       | Port number for GRPC service for data pods                                      | `17912`                                      |
| `cluster.data.nodeTemplate.sidecar`                            | Sidecar containers for data pods                                                | `[]`                                         |
| `cluster.data.nodeTemplate.backupSidecar.env`                  | Environment variables for the backup sidecar                                    | `[]`                                         |
| `cluster.data.nodeTemplate.backupSidecar.enabled`              | Enable backup sidecar for data pods (boolean)                                   | `false`                                      |
| `cluster.data.nodeTemplate.backupSidecar.dest`                 | Backup destination path for data pods                                           | `file:///tmp/backups/data-$(ORDINAL_NUMBER)` |
| `cluster.data.nodeTemplate.backupSidecar.timeStyle`            | Backup time style for data pods (e.g., daily)                                   | `daily`                                      |
| `cluster.data.nodeTemplate.backupSidecar.schedule`             | Backup schedule for data pods (cron format)                                     | `@hourly`                                    |
| `cluster.data.nodeTemplate.backupSidecar.customFlags`          | Custom flags for backup sidecar (e.g., S3, Azure, GCS configuration)            | `[]`                                         |
| `cluster.data.nodeTemplate.backupSidecar.resources`            | Resources for backup sidecar for data pods                                      | `{}`                                         |
| `cluster.data.nodeTemplate.lifecycleSidecar.env`               | Environment variables for the lifecycle sidecar                                 | `[]`                                         |
| `cluster.data.nodeTemplate.lifecycleSidecar.enabled`           | Enable lifecycle sidecar for data pods (boolean)                                | `false`                                      |
| `cluster.data.nodeTemplate.lifecycleSidecar.schedule`          | Schedule for lifecycle sidecar (cron format)                                    | `@hourly`                                    |
| `cluster.data.nodeTemplate.lifecycleSidecar.progressFile`      | Progress file path for lifecycle sidecar                                        | `""`                                         |
| `cluster.data.nodeTemplate.lifecycleSidecar.reportDir`         | Report directory path for lifecycle sidecar                                     | `""`                                         |
| `cluster.data.nodeTemplate.lifecycleSidecar.resources`         | Resources for lifecycle sidecar for data pods                                   | `{}`                                         |
| `cluster.data.nodeTemplate.restoreInitContainer.env`           | Environment variables for the restore init container                            | `[]`                                         |
| `cluster.data.nodeTemplate.restoreInitContainer.enabled`       | Enable restore init container for data pods (boolean)                           | `false`                                      |
| `cluster.data.nodeTemplate.restoreInitContainer.customFlags`   | Custom flags for restore init container (e.g., S3, Azure, GCS configuration)    | `[]`                                         |
| `cluster.data.nodeTemplate.restoreInitContainer.resources`     | Resources for restore init container for data pods                              | `{}`                                         |
| `cluster.data.nodeTemplate.livenessProbe.initialDelaySeconds`  | Initial delay for data liveness probe                                           | `20`                                         |
| `cluster.data.nodeTemplate.livenessProbe.periodSeconds`        | Probe period for data liveness probe                                            | `30`                                         |
| `cluster.data.nodeTemplate.livenessProbe.timeoutSeconds`       | Timeout in seconds for data liveness probe                                      | `5`                                          |
| `cluster.data.nodeTemplate.livenessProbe.successThreshold`     | Success threshold for data liveness probe                                       | `1`                                          |
| `cluster.data.nodeTemplate.livenessProbe.failureThreshold`     | Failure threshold for data liveness probe                                       | `5`                                          |
| `cluster.data.nodeTemplate.readinessProbe.initialDelaySeconds` | Initial delay for data readiness probe                                          | `20`                                         |
| `cluster.data.nodeTemplate.readinessProbe.periodSeconds`       | Probe period for data readiness probe                                           | `30`                                         |
| `cluster.data.nodeTemplate.readinessProbe.timeoutSeconds`      | Timeout in seconds for data readiness probe                                     | `5`                                          |
| `cluster.data.nodeTemplate.readinessProbe.successThreshold`    | Success threshold for data readiness probe                                      | `1`                                          |
| `cluster.data.nodeTemplate.readinessProbe.failureThreshold`    | Failure threshold for data readiness probe                                      | `5`                                          |
| `cluster.data.nodeTemplate.startupProbe.initialDelaySeconds`   | Initial delay for data startup probe                                            | `0`                                          |
| `cluster.data.nodeTemplate.startupProbe.periodSeconds`         | Probe period for data startup probe                                             | `10`                                         |
| `cluster.data.nodeTemplate.startupProbe.timeoutSeconds`        | Timeout in seconds for data startup probe                                       | `5`                                          |
| `cluster.data.nodeTemplate.startupProbe.successThreshold`      | Success threshold for data startup probe                                        | `1`                                          |
| `cluster.data.nodeTemplate.startupProbe.failureThreshold`      | Failure threshold for data startup probe                                        | `60`                                         |
| `cluster.data.roles`                                           | List of data roles (hot, warm, cold)                                            |                                              |
| `cluster.data.roles.hot`                                       | Hot data role                                                                   |                                              |
| `cluster.data.roles.hot.hasMetaRole`                           | Whether this role serves as the metadata role when using native property server | `true`                                       |

### Configuration for UI component

| Name                                                                | Description                                     | Value           |
| ------------------------------------------------------------------- | ----------------------------------------------- | --------------- |
| `cluster.ui.type`                                                   | UI deployment type (None, Standalone, Embedded) | `Embedded`      |
| `cluster.ui.standalone.replicas`                                    | Number of UI replicas                           | `1`             |
| `cluster.ui.standalone.podAnnotations`                              | Pod annotations for UI                          | `{}`            |
| `cluster.ui.standalone.securityContext`                             | Security context for UI pods                    | `{}`            |
| `cluster.ui.standalone.env`                                         | Environment variables for UI pods               | `[]`            |
| `cluster.ui.standalone.priorityClassName`                           | Priority class name for UI pods                 | `""`            |
| `cluster.ui.standalone.updateStrategy.type`                         | Update strategy type for UI pods                | `RollingUpdate` |
| `cluster.ui.standalone.updateStrategy.rollingUpdate.maxUnavailable` | Maximum unavailable pods for UI update          | `1`             |
| `cluster.ui.standalone.updateStrategy.rollingUpdate.maxSurge`       | Maximum surge pods for UI update                | `1`             |
| `cluster.ui.standalone.podDisruptionBudget`                         | Pod disruption budget for UI                    | `{}`            |
| `cluster.ui.standalone.tolerations`                                 | Tolerations for UI pods                         | `[]`            |
| `cluster.ui.standalone.nodeSelector`                                | Node selector for UI pods                       | `[]`            |
| `cluster.ui.standalone.affinity`                                    | Affinity rules for UI pods                      | `{}`            |
| `cluster.ui.standalone.podAffinityPreset`                           | Pod affinity preset for UI                      | `""`            |
| `cluster.ui.standalone.podAntiAffinityPreset`                       | Pod anti-affinity preset for UI                 | `soft`          |
| `cluster.ui.standalone.resources.requests`                          | Resource requests for UI pods                   | `[]`            |
| `cluster.ui.standalone.resources.limits`                            | Resource limits for UI pods                     | `[]`            |
| `cluster.ui.standalone.httpSvc.labels`                              | Labels for UI HTTP service                      | `{}`            |
| `cluster.ui.standalone.httpSvc.annotations`                         | Annotations for UI HTTP service                 | `{}`            |
| `cluster.ui.standalone.httpSvc.port`                                | Port for UI HTTP service                        | `17913`         |
| `cluster.ui.standalone.httpSvc.type`                                | Service type for UI HTTP service                | `LoadBalancer`  |
| `cluster.ui.standalone.httpSvc.externalIPs`                         | External IPs for UI HTTP service                | `[]`            |
| `cluster.ui.standalone.httpSvc.loadBalancerIP`                      | Load balancer IP for UI HTTP service            | `nil`           |
| `cluster.ui.standalone.httpSvc.loadBalancerSourceRanges`            | Allowed source ranges for UI HTTP service       | `[]`            |
| `cluster.ui.standalone.ingress.enabled`                             | Enable ingress for UI                           | `false`         |
| `cluster.ui.standalone.ingress.labels`                              | Labels for UI ingress                           | `{}`            |
| `cluster.ui.standalone.ingress.annotations`                         | Annotations for UI ingress                      | `{}`            |
| `cluster.ui.standalone.ingress.rules`                               | Ingress rules for UI                            | `[]`            |
| `cluster.ui.standalone.ingress.tls`                                 | TLS configuration for UI ingress                | `[]`            |
| `cluster.ui.standalone.livenessProbe.initialDelaySeconds`           | Initial delay for UI liveness probe             | `20`            |
| `cluster.ui.standalone.livenessProbe.periodSeconds`                 | Probe period for UI liveness probe              | `30`            |
| `cluster.ui.standalone.livenessProbe.timeoutSeconds`                | Timeout in seconds for UI liveness probe        | `5`             |
| `cluster.ui.standalone.livenessProbe.successThreshold`              | Success threshold for UI liveness probe         | `1`             |
| `cluster.ui.standalone.livenessProbe.failureThreshold`              | Failure threshold for UI liveness probe         | `5`             |
| `cluster.ui.standalone.readinessProbe.initialDelaySeconds`          | Initial delay for UI readiness probe            | `20`            |
| `cluster.ui.standalone.readinessProbe.periodSeconds`                | Probe period for UI readiness probe             | `30`            |
| `cluster.ui.standalone.readinessProbe.timeoutSeconds`               | Timeout in seconds for UI readiness probe       | `5`             |
| `cluster.ui.standalone.readinessProbe.successThreshold`             | Success threshold for UI readiness probe        | `1`             |
| `cluster.ui.standalone.readinessProbe.failureThreshold`             | Failure threshold for UI readiness probe        | `5`             |
| `cluster.ui.standalone.startupProbe.initialDelaySeconds`            | Initial delay for UI startup probe              | `0`             |
| `cluster.ui.standalone.startupProbe.periodSeconds`                  | Probe period for UI startup probe               | `10`            |
| `cluster.ui.standalone.startupProbe.timeoutSeconds`                 | Timeout in seconds for UI startup probe         | `5`             |
| `cluster.ui.standalone.startupProbe.successThreshold`               | Success threshold for UI startup probe          | `1`             |
| `cluster.ui.standalone.startupProbe.failureThreshold`               | Failure threshold for UI startup probe          | `60`            |

### Configuration for FODC (First Occurrence Data Collection)

| Name                   | Description                                  | Value  |
| ---------------------- | -------------------------------------------- | ------ |
| `cluster.fodc.enabled` | Enable FODC (both Agent and Proxy) (boolean) | `true` |

### FODC Proxy component

| Name                                                    | Description                                                             | Value                                           |
| ------------------------------------------------------- | ----------------------------------------------------------------------- | ----------------------------------------------- |
| `cluster.fodc.proxy.podAnnotations`                     | Pod annotations for Proxy                                               | `{}`                                            |
| `cluster.fodc.proxy.securityContext`                    | Security context for Proxy pods                                         | `{}`                                            |
| `cluster.fodc.proxy.containerSecurityContext`           | Container-level security context for Proxy                              | `{}`                                            |
| `cluster.fodc.proxy.env`                                | Environment variables for Proxy pods                                    | `[]`                                            |
| `cluster.fodc.proxy.priorityClassName`                  | Priority class name for Proxy pods                                      | `""`                                            |
| `cluster.fodc.proxy.podDisruptionBudget`                | Pod disruption budget for Proxy                                         | `{}`                                            |
| `cluster.fodc.proxy.tolerations`                        | Tolerations for Proxy pods                                              | `[]`                                            |
| `cluster.fodc.proxy.nodeSelector`                       | Node selector for Proxy pods                                            | `[]`                                            |
| `cluster.fodc.proxy.affinity`                           | Affinity rules for Proxy pods                                           | `{}`                                            |
| `cluster.fodc.proxy.podAffinityPreset`                  | Pod affinity preset for Proxy                                           | `""`                                            |
| `cluster.fodc.proxy.podAntiAffinityPreset`              | Pod anti-affinity preset for Proxy                                      | `soft`                                          |
| `cluster.fodc.proxy.resources.requests`                 | Resource requests for Proxy pods                                        | `[]`                                            |
| `cluster.fodc.proxy.resources.limits`                   | Resource limits for Proxy pods                                          | `[]`                                            |
| `cluster.fodc.proxy.image.repository`                   | Docker repository for FODC Proxy                                        | `ghcr.io/apache/skywalking-banyandb-fodc-proxy` |
| `cluster.fodc.proxy.image.tag`                          | Image tag/version for FODC Proxy (empty for latest)                     | `""`                                            |
| `cluster.fodc.proxy.image.pullPolicy`                   | Image pull policy for FODC Proxy                                        | `IfNotPresent`                                  |
| `cluster.fodc.proxy.grpcSvc.labels`                     | Labels for Proxy gRPC service                                           | `{}`                                            |
| `cluster.fodc.proxy.grpcSvc.annotations`                | Annotations for Proxy gRPC service                                      | `{}`                                            |
| `cluster.fodc.proxy.grpcSvc.port`                       | Port number for Proxy gRPC service (Agent connections)                  | `17912`                                         |
| `cluster.fodc.proxy.httpSvc.labels`                     | Labels for Proxy HTTP service                                           | `{}`                                            |
| `cluster.fodc.proxy.httpSvc.annotations`                | Annotations for Proxy HTTP service                                      | `{}`                                            |
| `cluster.fodc.proxy.httpSvc.port`                       | Port number for Proxy HTTP service                                      | `17913`                                         |
| `cluster.fodc.proxy.httpSvc.type`                       | Service type for Proxy HTTP service (ClusterIP, LoadBalancer, NodePort) | `LoadBalancer`                                  |
| `cluster.fodc.proxy.httpSvc.externalIPs`                | External IP addresses for Proxy HTTP service                            | `[]`                                            |
| `cluster.fodc.proxy.httpSvc.loadBalancerIP`             | Load balancer IP for Proxy HTTP service                                 | `nil`                                           |
| `cluster.fodc.proxy.httpSvc.loadBalancerSourceRanges`   | Allowed source ranges for Proxy HTTP service                            | `[]`                                            |
| `cluster.fodc.proxy.ingress.enabled`                    | Enable ingress for Proxy                                                | `false`                                         |
| `cluster.fodc.proxy.ingress.labels`                     | Labels for Proxy ingress                                                | `{}`                                            |
| `cluster.fodc.proxy.ingress.annotations`                | Annotations for Proxy ingress                                           | `{}`                                            |
| `cluster.fodc.proxy.ingress.rules`                      | Ingress rules for Proxy                                                 | `[]`                                            |
| `cluster.fodc.proxy.ingress.tls`                        | TLS configuration for Proxy ingress                                     | `[]`                                            |
| `cluster.fodc.proxy.config.agentHeartbeatTimeout`       | Timeout for considering agent offline                                   | `30s`                                           |
| `cluster.fodc.proxy.config.agentCleanupTimeout`         | Timeout for auto-unregistering offline agents                           | `5m`                                            |
| `cluster.fodc.proxy.config.maxAgents`                   | Maximum number of agents allowed to register                            | `1000`                                          |
| `cluster.fodc.proxy.config.grpcMaxMsgSize`              | Maximum gRPC message size in bytes                                      | `4194304`                                       |
| `cluster.fodc.proxy.config.httpReadTimeout`             | HTTP read timeout                                                       | `10s`                                           |
| `cluster.fodc.proxy.config.httpWriteTimeout`            | HTTP write timeout                                                      | `10s`                                           |
| `cluster.fodc.proxy.config.heartbeatInterval`           | Default heartbeat interval for agents                                   | `10s`                                           |
| `cluster.fodc.proxy.livenessProbe.initialDelaySeconds`  | Initial delay for Proxy liveness probe                                  | `10`                                            |
| `cluster.fodc.proxy.livenessProbe.periodSeconds`        | Probe period for Proxy liveness probe                                   | `30`                                            |
| `cluster.fodc.proxy.livenessProbe.timeoutSeconds`       | Timeout in seconds for Proxy liveness probe                             | `5`                                             |
| `cluster.fodc.proxy.livenessProbe.successThreshold`     | Success threshold for Proxy liveness probe                              | `1`                                             |
| `cluster.fodc.proxy.livenessProbe.failureThreshold`     | Failure threshold for Proxy liveness probe                              | `5`                                             |
| `cluster.fodc.proxy.readinessProbe.initialDelaySeconds` | Initial delay for Proxy readiness probe                                 | `10`                                            |
| `cluster.fodc.proxy.readinessProbe.periodSeconds`       | Probe period for Proxy readiness probe                                  | `30`                                            |
| `cluster.fodc.proxy.readinessProbe.timeoutSeconds`      | Timeout in seconds for Proxy readiness probe                            | `5`                                             |
| `cluster.fodc.proxy.readinessProbe.successThreshold`    | Success threshold for Proxy readiness probe                             | `1`                                             |
| `cluster.fodc.proxy.readinessProbe.failureThreshold`    | Failure threshold for Proxy readiness probe                             | `5`                                             |
| `cluster.fodc.proxy.startupProbe.initialDelaySeconds`   | Initial delay for Proxy startup probe                                   | `0`                                             |
| `cluster.fodc.proxy.startupProbe.periodSeconds`         | Probe period for Proxy startup probe                                    | `10`                                            |
| `cluster.fodc.proxy.startupProbe.timeoutSeconds`        | Timeout in seconds for Proxy startup probe                              | `5`                                             |
| `cluster.fodc.proxy.startupProbe.successThreshold`      | Success threshold for Proxy startup probe                               | `1`                                             |
| `cluster.fodc.proxy.startupProbe.failureThreshold`      | Failure threshold for Proxy startup probe                               | `60`                                            |

### FODC Agent sidecar

| Name                                                               | Description                                                                                                                                                                         | Value                                           |
| ------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------- |
| `cluster.fodc.agent.image.repository`                              | Docker repository for FODC Agent                                                                                                                                                    | `ghcr.io/apache/skywalking-banyandb-fodc-agent` |
| `cluster.fodc.agent.image.tag`                                     | Image tag/version for FODC Agent (defaults to same as main image)                                                                                                                   | `""`                                            |
| `cluster.fodc.agent.image.pullPolicy`                              | Image pull policy for FODC Agent                                                                                                                                                    | `IfNotPresent`                                  |
| `cluster.fodc.agent.env`                                           | Environment variables for Agent sidecar                                                                                                                                             | `[]`                                            |
| `cluster.fodc.agent.containerSecurityContext`                      | Container-level security context for Agent                                                                                                                                          | `{}`                                            |
| `cluster.fodc.agent.resources.requests`                            | Resource requests for Agent                                                                                                                                                         |                                                 |
| `cluster.fodc.agent.resources.requests[0].key`                     | Resource request key                                                                                                                                                                | `memory`                                        |
| `cluster.fodc.agent.resources.requests[0].value`                   | Resource request value                                                                                                                                                              | `256Mi`                                         |
| `cluster.fodc.agent.resources.limits`                              | Resource limits for Agent                                                                                                                                                           |                                                 |
| `cluster.fodc.agent.resources.limits[0].key`                       | Resource limit key                                                                                                                                                                  | `memory`                                        |
| `cluster.fodc.agent.resources.limits[0].value`                     | Resource limit value                                                                                                                                                                | `256Mi`                                         |
| `cluster.fodc.agent.metricsPort`                                   | Metrics port for Agent sidecar (prometheus-listen-addr flag)                                                                                                                        | `9090`                                          |
| `cluster.fodc.agent.config.pollMetricsInterval`                    | Interval for polling BanyanDB metrics (poll-metrics-interval flag)                                                                                                                  | `15s`                                           |
| `cluster.fodc.agent.config.heartbeatInterval`                      | Heartbeat interval to Proxy (heartbeat-interval flag)                                                                                                                               | `10s`                                           |
| `cluster.fodc.agent.config.reconnectInterval`                      | Reconnect interval when connection to Proxy is lost (reconnect-interval flag)                                                                                                       | `10s`                                           |
| `cluster.fodc.agent.config.ktmEnabled`                             | Enable Kernel Telemetry Module (affects max-metrics-memory-usage-percentage)                                                                                                        | `true`                                          |
| `cluster.fodc.agent.config.pollClusterStateInterval`               | Interval for polling cluster state from Proxy (poll-cluster-state-interval flag)                                                                                                    | `30s`                                           |
| `cluster.fodc.agent.config.crashCollection.enabled`                | Enable panic/crash diagnostics collection                                                                                                                                           | `true`                                          |
| `cluster.fodc.agent.config.crashCollection.dir`                    | Shared path where banyand writes panic.json and fodc-agent reads it                                                                                                                 | `/tmp/crash`                                    |
| `cluster.fodc.agent.config.crashCollection.maxArtifacts`           | Max crash artifact directories banyand retains (oldest removed first; 0 disables pruning)                                                                                           | `10`                                            |
| `cluster.fodc.agent.config.crashCollection.diagnosisMemoryPercent` | Set banyand GOMEMLIMIT to this percent of the cgroup memory limit, reserving headroom for post-panic diagnostics (0 disables)                                                       | `50`                                            |
| `cluster.fodc.agent.pressureProfiler.enabled`                      | Enable automatic heap+goroutine pprof capture under memory pressure                                                                                                                 | `true`                                          |
| `cluster.fodc.agent.pressureProfiler.triggerPercent`               | Capture when RSS / cgroup_limit reaches this percentage                                                                                                                             | `75`                                            |
| `cluster.fodc.agent.pressureProfiler.cooldown`                     | Minimum interval between two captures                                                                                                                                               | `5m`                                            |
| `cluster.fodc.agent.pressureProfiler.dir`                          | Directory (on the writable volume) where captured profiles are stored; must equal the pressure-profiles mount path                                                                  | `/tmp/pressure-profiles`                        |
| `cluster.fodc.agent.pressureProfiler.maxArtifacts`                 | Maximum number of capture events to retain (lowest-RSS evicted first)                                                                                                               | `16`                                            |
| `cluster.fodc.agent.pressureProfiler.maxDiskSize`                  | Maximum total on-disk size for retained events; case-insensitive, all suffixes are 1024-based (K/M/G/T, KB/MB/GB/TB, Ki/Mi/Gi/Ti), or a plain byte count; 0 disables the disk bound | `512Mi`                                         |
| `cluster.fodc.agent.livenessProbe.initialDelaySeconds`             | Initial delay for Agent liveness probe                                                                                                                                              | `90`                                            |
| `cluster.fodc.agent.livenessProbe.periodSeconds`                   | Probe period for Agent liveness probe                                                                                                                                               | `30`                                            |
| `cluster.fodc.agent.livenessProbe.timeoutSeconds`                  | Timeout in seconds for Agent liveness probe                                                                                                                                         | `5`                                             |
| `cluster.fodc.agent.livenessProbe.successThreshold`                | Success threshold for Agent liveness probe                                                                                                                                          | `1`                                             |
| `cluster.fodc.agent.livenessProbe.failureThreshold`                | Failure threshold for Agent liveness probe                                                                                                                                          | `5`                                             |
| `cluster.fodc.agent.readinessProbe.initialDelaySeconds`            | Initial delay for Agent readiness probe                                                                                                                                             | `60`                                            |
| `cluster.fodc.agent.readinessProbe.periodSeconds`                  | Probe period for Agent readiness probe                                                                                                                                              | `10`                                            |
| `cluster.fodc.agent.readinessProbe.timeoutSeconds`                 | Timeout in seconds for Agent readiness probe                                                                                                                                        | `5`                                             |
| `cluster.fodc.agent.readinessProbe.successThreshold`               | Success threshold for Agent readiness probe                                                                                                                                         | `1`                                             |
| `cluster.fodc.agent.readinessProbe.failureThreshold`               | Failure threshold for Agent readiness probe                                                                                                                                         | `12`                                            |
| `cluster.fodc.agent.startupProbe.initialDelaySeconds`              | Initial delay for Agent startup probe                                                                                                                                               | `30`                                            |
| `cluster.fodc.agent.startupProbe.periodSeconds`                    | Probe period for Agent startup probe                                                                                                                                                | `5`                                             |
| `cluster.fodc.agent.startupProbe.timeoutSeconds`                   | Timeout in seconds for Agent startup probe                                                                                                                                          | `3`                                             |
| `cluster.fodc.agent.startupProbe.successThreshold`                 | Success threshold for Agent startup probe                                                                                                                                           | `1`                                             |
| `cluster.fodc.agent.startupProbe.failureThreshold`                 | Failure threshold for Agent startup probe                                                                                                                                           | `60`                                            |

### Storage configuration for persistent volumes

| Name                                                        | Description                                             | Value                                                                  |
| ----------------------------------------------------------- | ------------------------------------------------------- | ---------------------------------------------------------------------- |
| `storage.data.enabled`                                      | Enable persistent storage for data nodes (boolean)      | `true`                                                                 |
| `storage.data.persistentVolumeClaims`                       | List of PVC configurations for data nodes               |                                                                        |
| `storage.data.persistentVolumeClaims[0].mountTargets`       | Mount targets for the PVC                               | `["measure"]`                                                          |
| `storage.data.persistentVolumeClaims[0].nodeRole`           | Node role this PVC is bound to (hot, warm, cold)        | `hot`                                                                  |
| `storage.data.persistentVolumeClaims[0].existingClaimName`  | Existing PVC name (if any)                              | `nil`                                                                  |
| `storage.data.persistentVolumeClaims[0].claimName`          | Name of the PVC                                         | `hot-measure-data`                                                     |
| `storage.data.persistentVolumeClaims[0].size`               | Size of the PVC                                         | `50Gi`                                                                 |
| `storage.data.persistentVolumeClaims[0].accessModes`        | Access modes for the PVC                                | `["ReadWriteOnce"]`                                                    |
| `storage.data.persistentVolumeClaims[0].storageClass`       | Storage class for the PVC                               | `nil`                                                                  |
| `storage.data.persistentVolumeClaims[0].volumeMode`         | Volume mode for the PVC                                 | `Filesystem`                                                           |
| `storage.data.persistentVolumeClaims[1].mountTargets`       | Mount targets for the PVC                               | `["stream"]`                                                           |
| `storage.data.persistentVolumeClaims[1].nodeRole`           | Node role this PVC is bound to                          | `hot`                                                                  |
| `storage.data.persistentVolumeClaims[1].existingClaimName`  | Existing PVC name (if any)                              | `nil`                                                                  |
| `storage.data.persistentVolumeClaims[1].claimName`          | Name of the PVC                                         | `hot-stream-data`                                                      |
| `storage.data.persistentVolumeClaims[1].size`               | Size of the PVC                                         | `50Gi`                                                                 |
| `storage.data.persistentVolumeClaims[1].accessModes`        | Access modes for the PVC                                | `["ReadWriteOnce"]`                                                    |
| `storage.data.persistentVolumeClaims[1].storageClass`       | Storage class for the PVC                               | `nil`                                                                  |
| `storage.data.persistentVolumeClaims[1].volumeMode`         | Volume mode for the PVC                                 | `Filesystem`                                                           |
| `storage.data.persistentVolumeClaims[2].mountTargets`       | Mount targets for the PVC                               | `["property"]`                                                         |
| `storage.data.persistentVolumeClaims[2].nodeRole`           | Node role this PVC is bound to                          | `hot`                                                                  |
| `storage.data.persistentVolumeClaims[2].existingClaimName`  | Existing PVC name (if any)                              | `nil`                                                                  |
| `storage.data.persistentVolumeClaims[2].claimName`          | Name of the PVC                                         | `hot-property-data`                                                    |
| `storage.data.persistentVolumeClaims[2].size`               | Size of the PVC                                         | `5Gi`                                                                  |
| `storage.data.persistentVolumeClaims[2].accessModes`        | Access modes for the PVC                                | `["ReadWriteOnce"]`                                                    |
| `storage.data.persistentVolumeClaims[2].storageClass`       | Storage class for the PVC                               | `nil`                                                                  |
| `storage.data.persistentVolumeClaims[2].volumeMode`         | Volume mode for the PVC                                 | `Filesystem`                                                           |
| `storage.data.persistentVolumeClaims[3].mountTargets`       | Mount targets for the PVC                               | `["trace"]`                                                            |
| `storage.data.persistentVolumeClaims[3].nodeRole`           | Node role this PVC is bound to                          | `hot`                                                                  |
| `storage.data.persistentVolumeClaims[3].existingClaimName`  | Existing PVC name (if any)                              | `nil`                                                                  |
| `storage.data.persistentVolumeClaims[3].claimName`          | Name of the PVC                                         | `hot-trace-data`                                                       |
| `storage.data.persistentVolumeClaims[3].size`               | Size of the PVC                                         | `50Gi`                                                                 |
| `storage.data.persistentVolumeClaims[3].accessModes`        | Access modes for the PVC                                | `["ReadWriteOnce"]`                                                    |
| `storage.data.persistentVolumeClaims[3].storageClass`       | Storage class for the PVC                               | `nil`                                                                  |
| `storage.data.persistentVolumeClaims[3].volumeMode`         | Volume mode for the PVC                                 | `Filesystem`                                                           |
| `storage.data.persistentVolumeClaims[4].mountTargets`       | Mount targets for the PVC                               | `["schema-property"]`                                                  |
| `storage.data.persistentVolumeClaims[4].nodeRole`           | Node role this PVC is bound to                          | `hot`                                                                  |
| `storage.data.persistentVolumeClaims[4].existingClaimName`  | Existing PVC name (if any)                              | `nil`                                                                  |
| `storage.data.persistentVolumeClaims[4].claimName`          | Name of the PVC                                         | `hot-schema-property-data`                                             |
| `storage.data.persistentVolumeClaims[4].size`               | Size of the PVC                                         | `5Gi`                                                                  |
| `storage.data.persistentVolumeClaims[4].accessModes`        | Access modes for the PVC                                | `["ReadWriteOnce"]`                                                    |
| `storage.data.persistentVolumeClaims[4].storageClass`       | Storage class for the PVC                               | `nil`                                                                  |
| `storage.data.persistentVolumeClaims[4].volumeMode`         | Volume mode for the PVC                                 | `Filesystem`                                                           |
| `storage.liaison.enabled`                                   | Enable persistent storage for liaison nodes (boolean)   | `true`                                                                 |
| `storage.liaison.persistentVolumeClaims`                    | List of PVC configurations for liaison nodes            |                                                                        |
| `storage.liaison.persistentVolumeClaims[0].mountTargets`    | Mount targets for the PVC                               | `["measure","stream","trace"]`                                         |
| `storage.liaison.persistentVolumeClaims[0].claimName`       | Name of the PVC                                         | `liaison-data`                                                         |
| `storage.liaison.persistentVolumeClaims[0].size`            | Size of the PVC                                         | `10Gi`                                                                 |
| `storage.liaison.persistentVolumeClaims[0].accessModes`     | Access modes for the PVC                                | `["ReadWriteOnce"]`                                                    |
| `storage.liaison.persistentVolumeClaims[0].storageClass`    | Storage class for the PVC                               | `nil`                                                                  |
| `storage.liaison.persistentVolumeClaims[0].volumeMode`      | Volume mode for the PVC                                 | `Filesystem`                                                           |
| `storage.standalone.enabled`                                | Enable persistent storage for standalone mode (boolean) | `false`                                                                |
| `storage.standalone.persistentVolumeClaims`                 | List of PVC configurations for standalone               |                                                                        |
| `storage.standalone.persistentVolumeClaims[0].mountTargets` | Mount targets for the PVC                               | `["measure","stream","metadata","property","trace","schema-property"]` |
| `storage.standalone.persistentVolumeClaims[0].claimName`    | Name of the PVC                                         | `standalone-data`                                                      |
| `storage.standalone.persistentVolumeClaims[0].size`         | Size of the PVC                                         | `200Gi`                                                                |
| `storage.standalone.persistentVolumeClaims[0].accessModes`  | Access modes for the PVC                                | `["ReadWriteOnce"]`                                                    |
| `storage.standalone.persistentVolumeClaims[0].storageClass` | Storage class for the PVC                               | `nil`                                                                  |
| `storage.standalone.persistentVolumeClaims[0].volumeMode`   | Volume mode for the PVC                                 | `Filesystem`                                                           |

### Service account configuration

| Name                         | Description                         | Value  |
| ---------------------------- | ----------------------------------- | ------ |
| `serviceAccount.create`      | Create a service account (boolean)  | `true` |
| `serviceAccount.annotations` | Annotations for the service account | `{}`   |
| `serviceAccount.name`        | Name of the service account         | `""`   |
