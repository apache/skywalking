# Support Flink Monitoring
## Motivation
Apache Flink is a framework and distributed processing engine for stateful computations over unbounded and bounded data streams. Now that Skywalking can monitor OpenTelemetry metrics, I want to add Flink monitoring via the OpenTelemetry Collector, which fetches metrics from its own Http Endpoint
to expose metrics data for Prometheus.

## Architecture Graph
There is no significant architecture-level change.

## Proposed Changes
Flink expose its metrics via HTTP endpoint to OpenTelemetry collector, using SkyWalking openTelemetry receiver to receive these metrics。
Provide cluster, instance, and endpoint dimensions monitoring.

### Flink Cluster Supported Metrics

| Monitoring Panel              | Unit  | Metric Name                                           | Description                                                                                       | Data Source      |
|-------------------------------|-------|-------------------------------------------------------|---------------------------------------------------------------------------------------------------|------------------|
| Running Jobs                  | Count | meter_flink_jobManager_running_job_number             | The number of running jobs.                                                                       | Flink JobManager |
| TaskManagers                  | Count | meter_flink_jobManager_taskManagers_registered_number | The number of taskManagers.                                                                       | Flink JobManager |
| Jvm Cpu Load                  | %     | meter_flink_jobManager_jvm_cpu_load                   | The number of the jobManager Jvm cpu load.                                                        | Flink JobManager |
| Jvm thread count              | Count | meter_flink_jobManager_jvm_thread_count               | The total number of the jobManager jvm threads.                                                   | Flink JobManager |
| Jvm Memory Heap Used          | MB    | meter_flink_jobManager_jvm_memory_heap_used           | The amount of the jobManager jvm memory heap used.                                                | Flink JobManager |
| Jvm Memory NonHeap Used       | MB    | meter_flink_jobManager_jvm_memory_NonHeap_used        | The amount of the jobManager jvm nonHeap memory used.                                             | Flink JobManager |
| Task Managers Slots Total     | Count | meter_flink_jobManager_taskManagers_slots_total       | The number of  total slots.                                                                       | Flink JobManager |
| Task Managers Slots Available | Count | meter_flink_jobManager_taskManagers_slots_available   | The number of available slots.                                                                    | Flink JobManager |
| Jvm Cpu Time                  | ms    | meter_flink_jobManager_jvm_cpu_time                   | The jobManager cpu time used by the Jvm.                                                          | Flink JobManager |
| Jvm Memory Heap Available     | MB    | meter_flink_jobManager_jvm_memory_heap_available      | The amount of the jobManager available JVM memory Heap.                                           | Flink JobManager |
| Jvm Memory NoHeap Available   | MB    | meter_flink_jobManager_jvm_memory_nonHeap_available   | The amount of the jobManager available JVM memory noHeap.                                         | Flink JobManager |
| Jvm Memory Metaspace Used     | MB    | meter_flink_jobManager_jvm_memory_metaspace_used      | The amount of the jobManager Used Jvm metaspace memory.                                           | Flink JobManager |
| Jvm Metaspace Available       | MB    | meter_flink_jobManager_jvm_memory_metaspace_available | The amount of the jobManager available Jvm Metaspace Memory.                                      | Flink JobManager |
| Jvm G1 Young Generation Count | Count | meter_flink_jobManager_jvm_g1_young_generation_count  | The number of the jobManager Jvm g1 young generation count.                                       | Flink JobManager |
| Jvm G1 Old Generation Count   | Count | meter_flink_jobManager_jvm_g1_old_generation_count    | The number of the jobManager Jvm g1 old generation count.                                         | Flink JobManager |
| Jvm G1 Young Generation Time  | Count | meter_flink_jobManager_jvm_g1_young_generation_time   | The time of the jobManager Jvm g1 young generation.                                               | Flink JobManager |
| Jvm G1 Old Generation Time    | ms    | meter_flink_jobManager_jvm_g1_old_generation_time     | The time of  Jvm g1 old generation.                                                               | Flink JobManager |
| Jvm G1 Old Generation Count   | Count | meter_flink_jobManager_jvm_all_garbageCollector_count | The number of the jobManager Jvm all garbageCollector count.                                      | Flink JobManager |
| Jvm All GarbageCollector Time | ms    | meter_flink_jobManager_jvm_all_garbageCollector_time  | The time spent performing garbage collection for the given (or all) collector for the jobManager. | Flink JobManager |


### Flink taskManager Supported Metrics

| Monitoring Panel                 | Unit    | Metric Name                                              | Description                                                                                                                                                                                        | Data Source       |
|----------------------------------|---------|----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| Jvm Cpu Load                     | %       | meter_flink_taskManager_jvm_cpu_load                     | The number of the Jvm cpu load.                                                                                                                                                                    | Flink TaskManager |
| Jvm Thread Count                 | Count   | meter_flink_taskManager_jvm_thread_count                 | The total number of jvm threads.                                                                                                                                                                   | Flink TaskManager |
| Jvm Memory Heap Used             | MB      | meter_flink_taskManager_jvm_memory_heap_used             | The amount of jvm memory heap used.                                                                                                                                                                | Flink TaskManager |
| Jvm Memory NonHeap Used          | MB      | meter_flink_taskManager_jvm_memory_nonHeap_used          | The amount of jvm nonHeap memory used.                                                                                                                                                             | Flink TaskManager |
| Jvm Cpu Time                     | ms      | meter_flink_taskManager_jvm_cpu_time                     | The cpu time used by the JVM.                                                                                                                                                                      | Flink TaskManager |
| Jvm Memory Heap Available        | MB      | meter_flink_taskManager_jvm_memory_heap_available        | The amount of available JVM memory Heap.                                                                                                                                                           | Flink TaskManager |
| Jvm Memory NonHeap Available     | MB      | meter_flink_taskManager_jvm_memory_nonHeap_available     | The amount of available JVM memory nonHeap.                                                                                                                                                        | Flink TaskManager |
| Jvm Memory Metaspace Used        | MB      | meter_flink_taskManager_jvm_memory_metaspace_used        | The amount of Used Jvm metaspace memory.                                                                                                                                                           | Flink TaskManager |
| Jvm Metaspace Available          | MB      | meter_flink_taskManager_jvm_memory_metaspace_available   | The amount of Available Jvm Metaspace Memory.                                                                                                                                                      | Flink TaskManager |
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


### Flink Job Supported Metrics

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

## Imported Dependencies libs and their licenses.
No new dependency.

## Compatibility
no breaking changes.

## General usage docs

This feature is out of the box.
