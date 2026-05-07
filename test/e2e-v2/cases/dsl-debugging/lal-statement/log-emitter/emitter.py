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

"""OTLP log emitter for the DSL debug LAL statement-granularity e2e.

Emits one log per second carrying the marker `e2e_dsldbg_statement` so the
seed LAL rule's text { regexp ... } matches and the multi-statement extractor
fires for every record.
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
SERVICE_NAME = os.environ.get("EMITTER_SERVICE", "e2e-dsldbg-stmt-svc")
INSTANCE_NAME = os.environ.get("EMITTER_INSTANCE", "e2e-dsldbg-stmt-i1")
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
    logger = logging.getLogger("e2e_dsldbg_stmt")
    logger.setLevel(logging.INFO)
    logger.addHandler(handler)

    print(
        f"dsldbg-stmt-emitter started — endpoint={ENDPOINT} service={SERVICE_NAME} "
        f"instance={INSTANCE_NAME} producer_interval={PRODUCER_INTERVAL_SECONDS}s",
        flush=True,
    )

    seq = 0
    while True:
        seq += 1
        logger.info("e2e_dsldbg_statement seq=%d", seq, extra={
            "marker": "e2e_dsldbg_statement",
            "service.name": SERVICE_NAME,
            "service.instance.id": INSTANCE_NAME,
        })
        time.sleep(PRODUCER_INTERVAL_SECONDS)


if __name__ == "__main__":
    main()
