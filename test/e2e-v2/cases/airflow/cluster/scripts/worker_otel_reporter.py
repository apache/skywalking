# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#!/usr/bin/env python3
# Airflow 2.10 Celery workers do not emit pool gauges via native OTel; approximate from Celery
# active tasks for e2e only (see test/e2e-v2/cases/airflow/README.md).
import os
import socket
import subprocess
import time

from opentelemetry import metrics
from opentelemetry.exporter.otlp.proto.http.metric_exporter import OTLPMetricExporter
from opentelemetry.metrics import Observation
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import HOST_NAME, SERVICE_NAME, Resource

HOST = os.environ.get("HOSTNAME") or socket.gethostname()
CLUSTER = os.environ.get("AIRFLOW_CLUSTER", "airflow-e2e-cluster")
ENDPOINT = os.environ.get(
    "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT",
    "http://otel-collector:4318/v1/metrics",
)
INTERVAL_MS = int(os.environ.get("OTEL_METRIC_EXPORT_INTERVAL", "30000"))
OPEN_SLOTS = float(os.environ.get("WORKER_OTEL_OPEN_SLOTS", "8"))


def _celery_active_tasks() -> float:
    cmds = [
        [
            "celery",
            "-A",
            "airflow.providers.celery.executors.celery_executor.app",
            "inspect",
            "active",
            "-d",
            f"celery@{HOST}",
        ],
        [
            "celery",
            "-A",
            "airflow.executors.celery_executor.app",
            "inspect",
            "active",
            "-d",
            f"celery@{HOST}",
        ],
    ]
    for cmd in cmds:
        try:
            proc = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            if proc.returncode != 0:
                continue
            count = proc.stdout.count("'name':")
            if count == 0:
                count = proc.stdout.count('"name":')
            return float(count)
        except (OSError, subprocess.SubprocessError):
            continue
    return 0.0


def pool_open_slots(_options):
    active = _celery_active_tasks()
    yield Observation(max(OPEN_SLOTS - active, 0.0), {"pool_name": "default_pool"})


def pool_running_slots(_options):
    yield Observation(_celery_active_tasks(), {"pool_name": "default_pool"})


def pool_deferred_slots(_options):
    yield Observation(0.0, {"pool_name": "default_pool"})


def main():
    resource = Resource.create(
        {
            HOST_NAME: HOST,
            SERVICE_NAME: "Airflow",
            "cluster": CLUSTER,
        }
    )
    reader = PeriodicExportingMetricReader(
        OTLPMetricExporter(endpoint=ENDPOINT),
        export_interval_millis=INTERVAL_MS,
    )
    metrics.set_meter_provider(
        MeterProvider(resource=resource, metric_readers=[reader])
    )
    meter = metrics.get_meter("airflow.worker.reporter")
    meter.create_observable_gauge("airflow.pool.open_slots", callbacks=[pool_open_slots])
    meter.create_observable_gauge(
        "airflow.pool.running_slots", callbacks=[pool_running_slots]
    )
    meter.create_observable_gauge(
        "airflow.pool.deferred_slots", callbacks=[pool_deferred_slots]
    )
    while True:
        time.sleep(INTERVAL_MS / 1000.0)


if __name__ == "__main__":
    main()
