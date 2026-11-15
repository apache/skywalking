# Backup and Restore Setup on Kubernetes

This guide explains how to configure and deploy the backup and restore process for the SkyWalking BanyanDB Helm chart in a Kubernetes environment.

**Caution:** The backup and restore features are supported by the BanyanDB image starting from version 0.8.0. Ensure that you are using the correct image version.

## 1. Configure Backup Sidecar

The chart supports running a backup sidecar alongside the main BanyanDB container. This sidecar runs the backup and timedir commands. To enable this:

- In your **values.yaml**, ensure the backup sidecar feature is enabled:
  - Under `cluster.data.backupSidecar.enabled`, set it to `true`.
  - Configure the remote backup destination path via `cluster.data.backupSidecar.dest`.

Example configuration snippet:

```yaml
cluster:
  data:
    nodeTemplate:
      backupSidecar:
        enabled: true
        # Set the remote backup destination (e.g., file:///backups)
        dest: "file:///backups"
```

The backup sidecar container will run with an entrypoint similar to:

```sh
backup run --grpc-addr=127.0.0.1:17912 ...
```

Customize the command-line parameters as required for your deployment.

## 2. Configure Restore via Init Container

The restore process is handled by an init container which runs before the main data container starts. This container uses the `restore` binary to read timedir marker files from a shared volume and synchronize local data.

To enable restore:

- In your **values.yaml**, enable the restore init container under `cluster.data.restoreInitContainer.enabled`:

  ```yaml
  cluster:
    data:
      nodeTemplate:
        # Enable the restore init container
        restoreInitContainer:
          enabled: true
          # Optionally, configure additional parameters such as:
          command: [ "--source=file:///backups" ]
  ```

- Ensure that the backup, restore, and main containers share the required volumes (e.g., for `/tmp/stream`, `/tmp/measure`, and `/tmp/property`). This is typically configured via the Kubernetes volume definitions in the StatefulSet.

## 3. Shared Volumes

Both the backup (or timedir utility) and the restore init container need access to shared data directories where marker files are written. In the chart:

- Shared volume mounts are defined in the StatefulSet (see `statefulset.yaml`).
- Make sure your persistent volume or persistent volume claim (PVC) configurations in **values.yaml** mount the intended directories.

## 4. Manual Operations via Backup Sidecar

For manual operations (such as checking logs, running diagnostic commands, or executing timedir-related commands), you may need to attach to the backup sidecar container. To do so:

1. Identify the pod running the backup sidecar:

   ```sh
   kubectl get pods -l app.kubernetes.io/name=banyandb
   ```

2. Attach to the backup sidecar container using `kubectl exec`. Replace `<pod-name>` with your pod's name and `<sidecar-container-name>` with the backup sidecar container name:

   ```sh
   kubectl exec -it <pod-name> -c <sidecar-container-name> -- /bin/sh
   ```

3. Once inside, run manual commands. For example, list the remote timedir files:

   ```sh
   restore timedir list --dest file:///backups
   ```

## 5. Performing Manual Restore Operations

### Setting Marker Files and Verifying Data

Before triggering a restore, you may need to create and verify timedir marker files.

#### Create Timedir Marker Files

```sh
restore timedir create 2025-02-12 \
  --stream-root /tmp \
  --measure-root /tmp \
  --property-root /tmp
```

#### Verify Timedir Files

```sh
restore timedir read \
  --stream-root /tmp \
  --measure-root /tmp \
  --property-root /tmp
```

### Triggering the Restore

**Important:** For Kubernetes deployments, the restore operation is executed by the init container when a pod starts. Therefore, to trigger a restore using the init container, you must recreate the pod.

Follow these steps to restore data:

1. Ensure that the timedir marker files are correctly set.
2. Delete the existing pod so that the StatefulSet creates a new one, causing the init container to run the restore command:

   ```sh
   kubectl delete pod <pod-name>
   ```

3. The new pod will start, and the init container will perform the restore process by:
   - Reading the timedir marker files (e.g., `/tmp/stream/time-dir`).
   - Comparing local data with the remote backup snapshot.
   - Removing orphaned files and deleting the marker files upon successful restoration.

Upon success, the restore tool automatically removes the timedir marker files.
