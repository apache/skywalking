# Data Node Lifecycle Management on Kubernetes

This guide explains how to configure and deploy SkyWalking BanyanDB data nodes with lifecycle management using the Helm chart in a Kubernetes environment.

**Note:** Lifecycle management is only available in the Helm values file `values-lifecycle.yaml`. The default `values.yaml` does **not** include lifecycle management options.

## 1. What is Lifecycle Management?

Lifecycle management automates the movement and retention of data across different data node roles (hot, warm, cold) based on policies and schedules. This helps optimize storage usage and cost by moving less frequently accessed data to cheaper storage.

## 2. Comparing `values.yaml` and `values-lifecycle.yaml`

- **`values.yaml`**: Only defines basic data node roles (e.g., `hot`), without lifecycle management or scheduling.
- **`values-lifecycle.yaml`**: Adds lifecycle management via the `lifecycleSidecar` for each data node role (`hot`, `warm`, `cold`). You can set schedules and enable/disable lifecycle management per role.

## 3. Enabling Lifecycle Management

To enable lifecycle management, use `values-lifecycle.yaml` and configure the `lifecycleSidecar` section under `cluster.data.nodeTemplate` or per role under `cluster.data.roles`.

Example configuration snippet:

```yaml
cluster:
  data:
    roles:
      hot:
        lifecycleSidecar:
          enabled: true
          schedule: "@daily"
      warm:
        lifecycleSidecar:
          enabled: true
          schedule: "@daily"
      cold:
        # cold nodes may not need lifecycle management
        lifecycleSidecar:
          enabled: false
```

- The `lifecycleSidecar` runs as a sidecar container in each data pod.
- You can override the schedule and enablement per role (`hot`, `warm`, `cold`).

## 4. How Lifecycle Management Works

- The lifecycle sidecar periodically runs according to the configured schedule.
- It manages data retention, migration, and cleanup based on the node's role and policy.
- For example, data may be moved from hot to warm nodes, or deleted from cold nodes after a retention period.

## 5. Deploying with Lifecycle Management

1. **Choose the correct values file**: Use `values-lifecycle.yaml` when installing or upgrading the Helm chart.
2. **Customize lifecycle settings**: Edit the `lifecycleSidecar` section as needed for your use case.
3. **Install or upgrade the chart**:

   ```sh
   helm install banyandb ./chart -f values-lifecycle.yaml
   # or, for upgrade:
   helm upgrade banyandb ./chart -f values-lifecycle.yaml
   ```

4. **Verify deployment**: Check that data pods have the lifecycle sidecar running:

   ```sh
   kubectl get pods -l app.kubernetes.io/name=banyandb
   kubectl describe pod <pod-name>
   ```

   You should see a container named similar to `lifecycle-sidecar` in each data pod.

## 6. Disabling Lifecycle Management

To disable lifecycle management for all or specific roles, set `enabled: false` under the relevant `lifecycleSidecar` section.

```yaml
cluster:
  data:
    nodeTemplate:
      lifecycleSidecar:
        enabled: false
    roles:
      hot:
        lifecycleSidecar:
          enabled: false
```

For more details, refer to the comments in `values-lifecycle.yaml` and the [official documentation](https://skywalking.apache.org/docs/).
