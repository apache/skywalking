Changes by Version
==================
Release Notes.

0.8.0
-----------------

#### Features

- Support Canopy SSO (OIDC/OAuth2) and break-glass (#14210):
  - Add `canopy.auth.backend` to choose between `local` (default) and `sso`.
  - Render the Canopy auth configuration into a mounted Secret and pass it via `CANOPY_AUTH_CONFIG` and `CANOPY_AUTH_BACKEND`.
  - Support mounting provider client secrets from Kubernetes Secrets via `clientSecretSecretRef` mapped to `clientSecretFile` or optional env vars.
  - Add `canopy.auth.breakGlass` for emergency local login when SSO health probe is degraded.
  - Support `canopy.auth.existingAuthConfigSecret` for user-provided auth configs while preserving full backward compatibility for `canopy.auth.existingSecret` and `local` mode.

#### Bugs

- Fix the duplicate data-node environment variables that made Helm 4 installs fail with "duplicate entries
  for key": a role that sets no `env` of its own inherited `cluster.data.nodeTemplate.env` and then got it a
  second time. The data container now merges `nodeTemplate.env` and the role's `env` by name; a role value
  overrides the template value of the same name.

0.7.0
-----------------

#### Features

- Add optional canopy web console deployment (`canopy.*`): Deployment + Service
  + optional Ingress for standalone and cluster modes, with a generated default
  admin user (strong random password, stable across upgrades; retrievable from
  the install notes or the `<release>-canopy-auth` Secret). The BanyanDB HTTP
  services now also expose the observability port 2121 so the console's
  monitoring target works in both modes.

- Remove etcd support (breaking change).
  If upgrading, remove `etcd-client.*` and `cluster.*.tls.etcdSecretName`
  from your values overrides.
- Disable the lifecycle metrics collector when the lifecycle sidecar container is disabled.
- Enable FODC panic/crash diagnostics collection by default.
  Configure via `cluster.fodc.agent.config.crashCollection.{enabled,dir,maxArtifacts,diagnosisMemoryPercent}`.
- Add FODC memory-pressure pprof capture for data and liaison nodes.
  Configure via `cluster.fodc.agent.pressureProfiler.*`.
- Add trace-pipeline sampler plugin support for data nodes (`plugins.*`):
  deploy the plugin host/carrier images, mount trusted and third-party plugin
  directories, and validate the plugin configuration at install time.
- Validate generated resource names (StatefulSets, Services, Secrets,
  Deployments) at install/upgrade time to avoid exceeding Kubernetes' 63-byte
  label limit, failing fast with a clear error instead of at apply time.
- Add an `env` list to the lifecycle sidecar, the backup sidecar and the restore init container.

0.6.0
-----------------

#### Features

-  Support configure node discovery in cluster mode.
-  Add the FODC Agent and Proxy components to the Helm chart.

0.5.3
-----------------

#### Bugs

- Fix missing port in data node list for liaison.

0.5.2
-----------------

#### Bugs

- Fix the incorrect data node list of the liaison.

0.5.1
-----------------

#### Features

-  Support data node list in the liaison

#### Bugs

- Fix missing env in the restore init container

0.5.0
------------------

#### Features

- Support Lifecycle Sidecar for automated data management across hot/warm/cold node roles with configurable schedules
- Introduce the data node template system to support different node roles (hot, warm, cold) with role-specific configurations
- Convert liaison component from Deployment to StatefulSet for improved state management and stable network identities
- Implement component-based storage configuration with separate data, liaison, and standalone sections. Enable external data and liaison storage by default with persistent volume claims
- Add headless services for StatefulSet pod discovery and stable network identities, enabling reliable pod-to-pod communication
- Add internal-grpc port 18912 for liaison pod-to-pod communication, enhancing cluster internal networking
- Enable etcd defragmentation by default with daily scheduling (0 0 * * *) to maintain optimal etcd performance
- Enhance pod hostname configuration using headless services for improved service discovery and networking
- Implement volume permissions init containers for proper file ownership and permissions on mounted volumes
- Add the mount target for the trace mode
- Add `auth` to configure the basic credential file.
- Set etcd's reposiotry to "bitnamilegacy". Bitnami removed non-hardened, Debian-based software images in its free tier under https://news.broadcom.com/app-dev/broadcom-introduces-bitnami-secure-images-for-production-ready-containerized-applications

#### Bugs

- Fix missing "trace" mount target in liaison and standalone storage configurations
- Fix typo "observability"

#### Chores

- Bump up e2e test cases.

0.4.0
------------------

#### Features

- Support Backup Sidecar and Restore Init Container
- Leave the image tag empty to force the users to specify the image tag
- Add a default volume for "property"

0.3.0
------------------

#### Features

- Support Anti-Affinity for banyandb cluster mode
- Align the modern Kubernetes label names
- Opt probe settings to http get /healthz instead of bydbctl health check
- Add standalone UI deployment

#### Chores

- Bump banyandb image version to 0.7.0


0.2.0
------------------

#### Features

- Support banyandb cluster mode
- Add e2e test to CI

#### Chores

- Update relevant documents

#### Chores

- Bump banyandb image version to 0.6.0
- Bump several dependencies in e2e test

0.1.0
------------------

#### Features
- Deploy banyandb with standalone mode by Chart
