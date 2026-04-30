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

"""Synthetic OTLP log emitter for the runtime-rule LAL live-swap e2e.

Pushes one log record per producer-interval to OAP via OTLP gRPC. The log
body carries a fixed marker ("e2e_rr_lal_live") that the LAL rule under test
matches on; the per-log severity rotates so a swap from "all-INFO" to
"only-ERROR" filtering produces a visible metric-count change.
"""

import logging
import os
import time

from opentelemetry._logs import set_logger_provider
from opentelemetry.exporter.otlp.proto.grpc._log_exporter import OTLPLogExporter
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
from opentelemetry.sdk.resources import Resource

ENDPOINT = os.environ.get("OTLP_ENDPOINT", "http://oap:11800")
SERVICE_NAME = os.environ.get("EMITTER_SERVICE", "e2e-rr-lal-svc")
INSTANCE_NAME = os.environ.get("EMITTER_INSTANCE", "e2e-rr-lal-i1")
PRODUCER_INTERVAL_SECONDS = float(os.environ.get("EMITTER_INTERVAL_S", "1"))


def main() -> None:
    resource = Resource.create({
        "service.name": SERVICE_NAME,
        "service.instance.id": INSTANCE_NAME,
    })
    provider = LoggerProvider(resource=resource)
    provider.add_log_record_processor(
        BatchLogRecordProcessor(OTLPLogExporter(endpoint=ENDPOINT, insecure=True))
    )
    set_logger_provider(provider)

    handler = LoggingHandler(level=logging.NOTSET, logger_provider=provider)
    logger = logging.getLogger("e2e_rr_lal")
    logger.setLevel(logging.INFO)
    logger.addHandler(handler)

    print(
        f"lal-log-emitter started — endpoint={ENDPOINT} service={SERVICE_NAME} "
        f"instance={INSTANCE_NAME} producer_interval={PRODUCER_INTERVAL_SECONDS}s",
        flush=True,
    )

    seq = 0
    while True:
        seq += 1
        # Alternate INFO / ERROR every other tick so the LAL filter has both shapes
        # to choose from; the swap test uses severity to differentiate "all logs"
        # from "errors only".
        level = logging.ERROR if seq % 2 == 0 else logging.INFO
        logger.log(level, "e2e_rr_lal_live seq=%d", seq, extra={
            "marker": "e2e_rr_lal_live",
            "service.name": SERVICE_NAME,
            "service.instance.id": INSTANCE_NAME,
        })
        time.sleep(PRODUCER_INTERVAL_SECONDS)


if __name__ == "__main__":
    main()
