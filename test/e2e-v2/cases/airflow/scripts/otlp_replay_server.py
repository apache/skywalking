#!/usr/bin/env python3
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

"""Airflow mock e2e OTLP JSON replay sidecar (Python)."""

import glob
import json
import logging
import os
import threading
import time

import grpc
from flask import Flask
from google.protobuf import json_format
from opentelemetry.proto.collector.metrics.v1 import metrics_service_pb2
from opentelemetry.proto.collector.metrics.v1 import metrics_service_pb2_grpc

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
LOG = logging.getLogger("otlp-replay")

OAP_HOST = os.environ.get("OAP_HOST", "127.0.0.1")
OAP_GRPC_PORT = int(os.environ.get("OAP_GRPC_PORT", "11800"))
DATA_DIR = os.environ.get("OTEL_METRICS_DATA_PATH", "/data/otel-metrics")

SEND_SEQ = 0
SEQ_LOCK = threading.Lock()

app = Flask(__name__)


def rewrite_node(node, nano_time, start_nano_time, seq):
    if isinstance(node, dict):
        if "timeUnixNano" in node:
            node["timeUnixNano"] = str(nano_time)
        if "startTimeUnixNano" in node:
            node["startTimeUnixNano"] = str(start_nano_time)
        if "asDouble" in node and "startTimeUnixNano" in node:
            node["asDouble"] = float(node["asDouble"]) + seq
        for value in node.values():
            rewrite_node(value, nano_time, start_nano_time, seq)
    elif isinstance(node, list):
        for item in node:
            rewrite_node(item, nano_time, start_nano_time, seq)


def send_metrics_once():
    global SEND_SEQ
    with SEQ_LOCK:
        SEND_SEQ += 1
        seq = SEND_SEQ

    if not os.path.isdir(DATA_DIR):
        msg = f"The path must be a folder: {DATA_DIR}"
        LOG.error(msg)
        return msg

    json_files = sorted(glob.glob(os.path.join(DATA_DIR, "*.json")))
    if not json_files:
        msg = f"The folder doesn't contain any json file: {DATA_DIR}"
        LOG.error(msg)
        return msg

    nano_time = int(time.time() * 1_000_000_000)
    start_nano_time = nano_time - 60_000_000_000

    channel = grpc.insecure_channel(f"{OAP_HOST}:{OAP_GRPC_PORT}")
    stub = metrics_service_pb2_grpc.MetricsServiceStub(channel)

    for path in json_files:
        with open(path, encoding="utf-8") as handle:
            payload = json.load(handle)
        rewrite_node(payload, nano_time, start_nano_time, seq)
        request = metrics_service_pb2.ExportMetricsServiceRequest()
        json_format.Parse(json.dumps(payload), request, ignore_unknown_fields=True)
        try:
            stub.Export(request, timeout=10)
        except grpc.RpcError as error:
            LOG.error("sendOtelMetrics by template error: %s", error)
            channel.close()
            return str(error)

    channel.close()
    return "ok"


@app.get("/otel-metrics/send")
def send_endpoint():
    return send_metrics_once()


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=9093, threaded=True)
