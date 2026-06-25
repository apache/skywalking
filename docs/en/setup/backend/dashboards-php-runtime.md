# PHP runtime metrics (PHM)

The SkyWalking PHP agent can report **PHP Health Metrics (PHM)** through the native Meter protocol.
OAP parses them with MAL rules in `php-runtime.yaml` and stores
them as `meter_*` metrics on the **General Service** layer.

Requires a PHP agent build that includes PHM (merged in `apache/skywalking-php` 1.2.0+).

## Platform support

PHM process meters are **Linux only**. In `grpc` / `kafka` reporter mode, the forked reporter worker
samples the **parent PHP process** (`getppid()`) through `/proc` (`/proc/{pid}/status`, `stat`, and
`fd`). They are not collected on macOS or Windows, and PHM does not run when `reporter_type =
standalone`. Instance dashboard widgets stay hidden when no `meter_instance_php_*` data exists.

## Data flow

1. PHM is **On by default on Linux** when the agent is active (`skywalking_agent.enable = On`). Set
   `skywalking_agent.metrics_enable = Off` to disable.
2. The forked reporter worker boots `skywalking::metrics::Metricer` in `start_worker` and samples
   `/proc` on `metrics_report_period` (default 30 seconds). No HTTP traffic is required.
3. OAP loads `meter-analyzer-config/php-runtime.yaml` when `php-runtime` is listed in
   `agent-analyzer.default.meterAnalyzerActiveFiles`.
4. Horizon UI renders widgets on **General Service → Instance → Dashboard** when the corresponding
   `meter_instance_php_*` metrics exist.

## Agent setup

```ini
; Default On on Linux when the agent is active.
; skywalking_agent.metrics_enable = Off

skywalking_agent.metrics_report_period = 30
```

Refer to the PHP agent README and INI settings documentation for details.

## OAP setup

Ensure `php-runtime` is active (included by default when using the stock `application.yml`):

```yaml
agent-analyzer:
  default:
    meterAnalyzerActiveFiles: ...,php-runtime,...
```

## UI location

**Layer:** General Service (`GENERAL`)

**Path:** select a PHP service → **Instance** → **Dashboard**

Widgets appear only when PHM data is present (`visibleWhen` checks each `meter_instance_php_*` expression).

## Runtime metrics

Agent meter names (reported by the PHP agent) are rewritten by OAP MAL `metricPrefix: meter`:

| Unit  | Agent meter name                         | OAP / UI metric name                       | Description                             | Data Source          |
|-------|------------------------------------------|--------------------------------------------|-----------------------------------------|----------------------|
| %     | instance_php_process_cpu_utilization     | meter_instance_php_process_cpu_utilization | Process CPU utilization                 | SkyWalking PHP Agent |
| MB    | instance_php_memory_used_mb              | meter_instance_php_memory_used_mb          | Resident memory (VmRSS from /proc)      | SkyWalking PHP Agent |
| MB    | instance_php_memory_peak_mb              | meter_instance_php_memory_peak_mb          | Peak resident memory (VmHWM from /proc) | SkyWalking PHP Agent |
| MB    | instance_php_virtual_memory_mb           | meter_instance_php_virtual_memory_mb       | Virtual memory (VmSize from /proc)    | SkyWalking PHP Agent |
| —     | instance_php_thread_count                | meter_instance_php_thread_count            | OS thread count (Threads from /proc)    | SkyWalking PHP Agent |
| —     | instance_php_open_fd_count               | meter_instance_php_open_fd_count           | Open file descriptor count              | SkyWalking PHP Agent |

## Customizations

You can customize MAL expressions or dashboard panels. Metric definitions and expression rules are in
`/meter-analyzer-config/php-runtime.yaml`. Instance dashboard widget templates ship from the
SkyWalking Horizon UI bundle (`general.json` in apache/skywalking-horizon-ui).
