# Flink monitoring

## Flink server performance from built-in metrics data
SkyWalking leverages OpenTelemetry Collector to transfer the flink metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/mal.md).

## Data flow

1. Configure Flink jobManager and TaskManager to expose metrics data for scraping from Prometheus.
2. OpenTelemetry Collector fetches metrics from Flink jobManager and TaskManager through Prometheus endpoint, and pushes metrics to SkyWalking OAP Server via
   OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to
   filter/calculate/aggregate and store the results.

## Setup

1. Set up [built-in prometheus endpoint](https://nightlies.apache.org/flink/flink-docs-release-2.0-preview1/docs/deployment/metric_reporters/#prometheus).
2. Set up [OpenTelemetry Collector ](https://opentelemetry.io/docs/collector/getting-started/#docker).
   Please note that the OpenTelemetry Collector uses the job_name label by default, which may conflict with the job_name label in Flink. 
   Please modify the Flink label name in the configuration to avoid this conflict, you can refer to [here](../../../../test/e2e-v2/cases/flink/otel-collector-config.yaml)
   for details on Prometheus Receiver in OpenTelemetry Collector.
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

## Flink Monitoring

Flink monitoring provides multidimensional metrics monitoring of Flink cluster as `Layer: Flink` `Service` in
the OAP. In each cluster, the taskManager is represented as `Instance` and the job is represented as `Endpoint`.

### Flink service Supported Metrics

| Monitoring Panel              | Unit  | Metric Name                                           | Description                                                                                       | Data Source      |
|-------------------------------|-------|-------------------------------------------------------|---------------------------------------------------------------------------------------------------|------------------|
| Running Jobs                  | Count | meter_flink_jobManager_running_job_number             | The number of running jobs.                                                                       | Flink JobManager |
| TaskManagers                  | Count | meter_flink_jobManager_taskManagers_registered_number | The number of taskManagers.                                                                       | Flink JobManager |
| JVM CPU Load                  | %     | meter_flink_jobManager_jvm_cpu_load                   | The number of the jobManager JVM CPU load.                                                        | Flink JobManager |
| JVM thread count              | Count | meter_flink_jobManager_jvm_thread_count               | The total number of the jobManager JVM live threads.                                              | Flink JobManager |
| JVM Memory Heap Used          | MB    | meter_flink_jobManager_jvm_memory_heap_used           | The amount of the jobManager JVM memory heap used.                                                | Flink JobManager |
| JVM Memory NonHeap Used       | MB    | meter_flink_jobManager_jvm_memory_NonHeap_used        | The amount of the jobManager JVM nonHeap memory used.                                             | Flink JobManager |
| Task Managers Slots Total     | Count | meter_flink_jobManager_taskManagers_slots_total       | The number of  total slots.                                                                       | Flink JobManager |
| Task Managers Slots Available | Count | meter_flink_jobManager_taskManagers_slots_available   | The number of available slots.                                                                    | Flink JobManager |
| JVM CPU Time                  | ms    | meter_flink_jobManager_jvm_cpu_time                   | The jobManager CPU time used by the JVM.                                                          | Flink JobManager |
| JVM Memory Heap Available     | MB    | meter_flink_jobManager_jvm_memory_heap_available      | The amount of the jobManager available JVM memory Heap.                                           | Flink JobManager |
| JVM Memory NoHeap Available   | MB    | meter_flink_jobManager_jvm_memory_nonHeap_available   | The amount of the jobManager available JVM memory noHeap.                                         | Flink JobManager |
| JVM Memory Metaspace Used     | MB    | meter_flink_jobManager_jvm_memory_metaspace_used      | The amount of the jobManager Used JVM metaspace memory.                                           | Flink JobManager |
| JVM Metaspace Available       | MB    | meter_flink_jobManager_jvm_memory_metaspace_available | The amount of the jobManager available JVM Metaspace Memory.                                      | Flink JobManager |
| JVM G1 Young Generation Count | Count | meter_flink_jobManager_jvm_g1_young_generation_count  | The number of the jobManager JVM g1 young generation count.                                       | Flink JobManager |
| JVM G1 Old Generation Count   | Count | meter_flink_jobManager_jvm_g1_old_generation_count    | The number of the jobManager JVM g1 old generation count.                                         | Flink JobManager |
| JVM G1 Young Generation Time  | Count | meter_flink_jobManager_jvm_g1_young_generation_time   | The time of the jobManager JVM g1 young generation.                                               | Flink JobManager |
| JVM G1 Old Generation Time    | ms    | meter_flink_jobManager_jvm_g1_old_generation_time     | The time of  JVM g1 old generation.                                                               | Flink JobManager |
| JVM G1 Old Generation Count   | Count | meter_flink_jobManager_jvm_all_garbageCollector_count | The number of the jobManager JVM all garbageCollector count.                                      | Flink JobManager |
| JVM All GarbageCollector Time | ms    | meter_flink_jobManager_jvm_all_garbageCollector_time  | The time spent performing garbage collection for the given (or all) collector for the jobManager. | Flink JobManager |

### Flink instance Supported Metrics

| Monitoring Panel                 | Unit    | Metric Name                                              | Description                                                                                                                                                                                        | Data Source       |
|----------------------------------|---------|----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| JVM CPU Load                     | %       | meter_flink_taskManager_jvm_cpu_load                     | The number of the JVM CPU load.                                                                                                                                                                    | Flink TaskManager |
| JVM Thread Count                 | Count   | meter_flink_taskManager_jvm_thread_count                 | The total number of JVM threads.                                                                                                                                                                   | Flink TaskManager |
| JVM Memory Heap Used             | MB      | meter_flink_taskManager_jvm_memory_heap_used             | The amount of JVM memory heap used.                                                                                                                                                                | Flink TaskManager |
| JVM Memory NonHeap Used          | MB      | meter_flink_taskManager_jvm_memory_nonHeap_used          | The amount of JVM nonHeap memory used.                                                                                                                                                             | Flink TaskManager |
| JVM CPU Time                     | ms      | meter_flink_taskManager_jvm_cpu_time                     | The CPU time used by the JVM.                                                                                                                                                                      | Flink TaskManager |
| JVM Memory Heap Available        | MB      | meter_flink_taskManager_jvm_memory_heap_available        | The amount of available JVM memory Heap.                                                                                                                                                           | Flink TaskManager |
| JVM Memory NonHeap Available     | MB      | meter_flink_taskManager_jvm_memory_nonHeap_available     | The amount of available JVM memory nonHeap.                                                                                                                                                        | Flink TaskManager |
| JVM Memory Metaspace Used        | MB      | meter_flink_taskManager_jvm_memory_metaspace_used        | The amount of Used JVM metaspace memory.                                                                                                                                                           | Flink TaskManager |
| JVM Metaspace Available          | MB      | meter_flink_taskManager_jvm_memory_metaspace_available   | The amount of Available JVM Metaspace Memory.                                                                                                                                                      | Flink TaskManager |
| NumRecordsIn                     | Count   | meter_flink_taskManager_numRecordsIn                     | The total number of records this task has received.                                                                                                                                                | Flink TaskManager |
| NumRecordsOut                    | Count   | meter_flink_taskManager_numRecordsOut                    | The total number of records this task has emitted.                                                                                                                                                 | Flink TaskManager |
| NumBytesInPerSecond              | Bytes/s | meter_flink_taskManager_numBytesInPerSecond              | The number of bytes received per second.                                                                                                                                                           | Flink TaskManager |
| NumBytesOutPerSecond             | Bytes/s | meter_flink_taskManager_numBytesOutPerSecond             | The number of bytes this task emits per second.                                                                                                                                                    | Flink TaskManager |
| Netty UsedMemory                 | MB      | meter_flink_taskManager_netty_usedMemory                 | The amount of used netty memory.                                                                                                                                                                   | Flink TaskManager |
| Netty AvailableMemory            | MB      | meter_flink_taskManager_netty_availableMemory            | The amount of available netty memory.                                                                                                                                                              | Flink TaskManager |
| IsBackPressured                  | Count   | meter_flink_taskManager_isBackPressured                  | Whether the task is back-pressured.                                                                                                                                                                | Flink TaskManager |
| InPoolUsage                      | %       | meter_flink_taskManager_inPoolUsage                      | An estimate of the input buffers usage. (ignores LocalInputChannels).                                                                                                                              | Flink TaskManager |
| OutPoolUsage                     | %       | meter_flink_taskManager_outPoolUsage                     | An estimate of the output buffers usage. The pool usage can be > 100% if overdraft buffers are being used.                                                                                         | Flink TaskManager |
| SoftBackPressuredTimeMsPerSecond | ms      | meter_flink_taskManager_softBackPressuredTimeMsPerSecond | The time this task is softly back pressured per second.Softly back pressured task will be still responsive and capable of for example triggering unaligned checkpoints.                            | Flink TaskManager |
| HardBackPressuredTimeMsPerSecond | ms      | meter_flink_taskManager_hardBackPressuredTimeMsPerSecond | The time this task is back pressured in a hard way per second.During hard back pressured task is completely blocked and unresponsive preventing for example unaligned checkpoints from triggering. | Flink TaskManager |
| IdleTimeMsPerSecond              | ms      | meter_flink_taskManager_idleTimeMsPerSecond              | The time this task is idle (has no data to process) per second. Idle time excludes back pressured time, so if the task is back pressured it is not idle.                                           | Flink TaskManager |
| BusyTimeMsPerSecond              | ms      | meter_flink_taskManager_busyTimeMsPerSecond              | The time this task is busy (neither idle nor back pressured) per second. Can be NaN, if the value could not be calculated.                                                                         | Flink TaskManager |

### Flink Endpoint Supported Metrics

| Monitoring Panel        | Unit    | Metric Name                             | Description                                                                                                                                                            | Data Source       |
|-------------------------|---------|-----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| Job RunningTime         | min     | meter_flink_job_runningTime             | The job running time.                                                                                                                                                  | Flink JobManager  |
| Job Restart Number      | Count   | meter_flink_job_restart_number          | The number of job restart.                                                                                                                                             | Flink JobManager  |
| Job RestartingTime      | min     | meter_flink_job_restartingTime          | The job restarting Time.                                                                                                                                               | Flink JobManager  |
| Job CancellingTime      | min     | meter_flink_job_cancellingTime          | The job cancelling time.                                                                                                                                               | Flink JobManager  |
| Checkpoints Total       | Count   | meter_flink_job_checkpoints_total       | The total number of checkpoints.                                                                                                                                       | Flink JobManager  |
| Checkpoints Failed      | Count   | meter_flink_job_checkpoints_failed      | The number of failed checkpoints.                                                                                                                                      | Flink JobManager  |
| Checkpoints Completed   | Count   | meter_flink_job_checkpoints_completed   | The number of completed checkpoints.                                                                                                                                   | Flink JobManager  |
| Checkpoints InProgress  | Count   | meter_flink_job_checkpoints_inProgress  | The number of inProgress checkpoints.                                                                                                                                  | Flink JobManager  |
| CurrentEmitEventTimeLag | ms      | meter_flink_job_currentEmitEventTimeLag | The latency between a data record's event time and its emission time from the source.                                                                                  | Flink TaskManager |
| NumRecordsIn            | Count   | meter_flink_job_numRecordsIn            | The total number of records this operator/task has received.                                                                                                           | Flink TaskManager |
| NumRecordsOut           | Count   | meter_flink_job_numRecordsOut           | The total number of records this operator/task has emitted.                                                                                                            | Flink TaskManager |
| NumBytesInPerSecond     | Bytes/s | meter_flink_job_numBytesInPerSecond     | The number of bytes this task received per second.                                                                                                                     | Flink TaskManager |
| NumBytesOutPerSecond    | Bytes/s | meter_flink_job_numBytesOutPerSecond    | The number of bytes this task emits per second.                                                                                                                        | Flink TaskManager |
| LastCheckpointSize      | Bytes   | meter_flink_job_lastCheckpointSize      | The checkPointed size of the last checkpoint (in bytes), this metric could be different from lastCheckpointFullSize if incremental checkpoint or changelog is enabled. | Flink JobManager  |
| LastCheckpointDuration  | ms      | meter_flink_job_lastCheckpointDuration  | The time it took to complete the last checkpoint.                                                                                                                      | Flink JobManager  |

## Customizations

You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found
in `otel-rules/flink/flink-jobManager.yaml, otel-rules/flink/flink-taskManager.yaml, otel-rules/flink/flink-job.yaml`.
The Flink dashboard panel configurations are found in `ui-initialized-templates/flink`.