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

"""Synthetic OTLP metric emitter for the runtime-rule storage-matrix e2e.

Emits two metrics on a steady cadence so the runtime-rule MAL pipeline
has predictable input regardless of which backend the OAP under test is
talking to. Names are deliberately namespaced with ``e2e_rr_`` so they
do not collide with any static rule shipped by OAP.

Counter ``e2e_rr_request_count_total`` is monotonically increasing and
drives the FILTER_ONLY / STRUCTURAL apply assertions through ``sum(...)``
in MAL. Gauge ``e2e_rr_pool_size`` is held constant so STRUCTURAL adds
have a second metric to derive from without coupling to time.

The emitter sleeps a short interval between samples so the L1 / L2
aggregation pipeline on OAP has steady ticks; the OTLP exporter's own
periodic reader pushes whatever has been recorded since the last flush.
"""
import os
import time

from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import Resource

ENDPOINT = os.environ.get("OTLP_ENDPOINT", "http://oap:11800")
SERVICE_NAME = os.environ.get("EMITTER_SERVICE", "e2e-rr-svc")
INSTANCE_NAME = os.environ.get("EMITTER_INSTANCE", "e2e-rr-i1")

# 5 s OTLP export interval so OAP sees fresh data within one minute bucket.
EXPORT_INTERVAL_MILLIS = int(os.environ.get("OTLP_EXPORT_INTERVAL_MS", "5000"))
# 2 s producer sleep — independent of the export interval so we always have
# at least one observation per export window.
PRODUCER_INTERVAL_SECONDS = float(os.environ.get("EMITTER_INTERVAL_S", "2"))

# Shared file the flow script rewrites between phases. Each emitter tick
# reads the file so samples carry the *current* phase's `step` label and
# the lifecycle e2e can attribute storage rows back to the phase that
# wrote them. Defaults to "create" for back-compat with the simpler flow.
STEP_FILE = os.environ.get("STEP_FILE", "/shared/step")
STEP_DEFAULT = os.environ.get("STEP_DEFAULT", "create")


def read_step() -> str:
    try:
        with open(STEP_FILE, "r") as f:
            value = f.read().strip()
            return value or STEP_DEFAULT
    except FileNotFoundError:
        return STEP_DEFAULT


def main() -> None:
    resource = Resource.create({
        "service.name": SERVICE_NAME,
        "service.instance.id": INSTANCE_NAME,
    })

    exporter = OTLPMetricExporter(endpoint=ENDPOINT, insecure=True)
    reader = PeriodicExportingMetricReader(
        exporter,
        export_interval_millis=EXPORT_INTERVAL_MILLIS,
    )
    provider = MeterProvider(resource=resource, metric_readers=[reader])
    meter = provider.get_meter("e2e-rr-otlp-emitter")

    counter = meter.create_counter(
        name="e2e_rr_request_count_total",
        description="Synthetic request counter for runtime-rule e2e.",
    )

    # Hold the pool-size gauge constant — an ObservableGauge needs a callback
    # but the value is otherwise stable so STRUCTURAL assertions can pin a
    # specific number. The callback reads the current step so samples produced
    # via the gauge's periodic export carry the same label as the counter's.
    def pool_size_callback(_options):
        from opentelemetry.metrics import Observation
        step = read_step()
        return [
            Observation(value=42, attributes={
                "service.name": SERVICE_NAME,
                "service.instance.id": INSTANCE_NAME,
                "step": step,
            }),
        ]

    meter.create_observable_gauge(
        name="e2e_rr_pool_size",
        callbacks=[pool_size_callback],
        description="Synthetic pool-size gauge for runtime-rule e2e.",
    )

    print(
        f"otlp-emitter started — endpoint={ENDPOINT} service={SERVICE_NAME} "
        f"instance={INSTANCE_NAME} producer_interval={PRODUCER_INTERVAL_SECONDS}s "
        f"export_interval={EXPORT_INTERVAL_MILLIS}ms step_file={STEP_FILE}",
        flush=True,
    )

    last_step = None
    while True:
        step = read_step()
        if step != last_step:
            print(f"otlp-emitter: step={step}", flush=True)
            last_step = step
        counter.add(1, attributes={
            "service.name": SERVICE_NAME,
            "service.instance.id": INSTANCE_NAME,
            "step": step,
        })
        time.sleep(PRODUCER_INTERVAL_SECONDS)


if __name__ == "__main__":
    main()
