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

# Optional "noise" emission under additional (service.name, service.instance.id)
# pairs. Each tick, samples are also pushed under each noise pair so the
# resulting MAL SampleFamily has MULTIPLE items at the file-level filter
# stage — the dsl-debugging mal e2e uses this to demonstrate filter
# narrowing in capture payloads. Default empty = single-service shape
# unchanged for the runtime-rule storage e2e.
# Format: "svc1=inst1,svc2=inst2" — comma-separated pairs.
NOISE_PAIRS_RAW = os.environ.get("EMITTER_NOISE_PAIRS", "")

# Optional "decoy" metric emission. When set, the emitter pushes a third
# metric (`e2e_rr_decoy_total`) under a SECONDARY service.name so a
# multi-metric MAL rule that references all three (request_count + pool_size
# + decoy) sees the decoy's family enter the rule's input but the file-level
# filter rejects it (the decoy's tags don't match the predicate). The
# captured `filter` sample then shows the decoy ABSENT from the surviving
# families list, demonstrating filter narrowing across SampleFamilies.
# Empty default = no decoy.
DECOY_METRIC_NAME = os.environ.get("EMITTER_DECOY_METRIC", "")
DECOY_SERVICE = os.environ.get("EMITTER_DECOY_SERVICE", "e2e-decoy-svc")
DECOY_INSTANCE = os.environ.get("EMITTER_DECOY_INSTANCE", "e2e-decoy-i1")

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


def parse_noise_pairs(raw: str):
    """Parse {@code EMITTER_NOISE_PAIRS} into a list of (service, instance) tuples.
    Empty string → empty list, preserving the single-service default."""
    if not raw or not raw.strip():
        return []
    pairs = []
    for entry in raw.split(","):
        entry = entry.strip()
        if not entry:
            continue
        if "=" in entry:
            svc, inst = entry.split("=", 1)
        else:
            svc, inst = entry, "noise-1"
        pairs.append((svc.strip(), inst.strip()))
    return pairs


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

    decoy_counter = None
    if DECOY_METRIC_NAME:
        decoy_counter = meter.create_counter(
            name=DECOY_METRIC_NAME,
            description="Decoy counter pushed under a secondary service.name "
                        "so the file-level MAL filter rejects its family — "
                        "lets the dsl-debugging mal e2e demonstrate filter "
                        "narrowing across multiple metric families.",
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

    noise_pairs = parse_noise_pairs(NOISE_PAIRS_RAW)
    if noise_pairs:
        print(f"otlp-emitter: noise pairs = {noise_pairs}", flush=True)

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
        # Noise samples — share the same metric name + step but carry
        # different (service.name, service.instance.id) labels. The
        # downstream MAL pipeline sees a multi-item SampleFamily, and
        # the file-level filter narrows back to the primary service.
        for noise_svc, noise_inst in noise_pairs:
            counter.add(1, attributes={
                "service.name": noise_svc,
                "service.instance.id": noise_inst,
                "step": step,
            })
        # Decoy metric — pushed under a secondary service.name only, so a
        # rule whose file-level filter requires the primary service rejects
        # the entire decoy family at filter time.
        if decoy_counter is not None:
            decoy_counter.add(1, attributes={
                "service.name": DECOY_SERVICE,
                "service.instance.id": DECOY_INSTANCE,
                "step": step,
            })
        time.sleep(PRODUCER_INTERVAL_SECONDS)


if __name__ == "__main__":
    main()
