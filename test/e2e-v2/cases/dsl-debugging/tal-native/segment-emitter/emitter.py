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

"""Synthetic native segment emitter for the TAL DSL debug API e2e.

Every interval, one segment of the typical shape a SkyWalking agent reports for a request: an Entry span, a Local
span under it, and two Exit spans under that, one to a database and one to a cache, which the shipped native rules
turn into the virtual database and virtual cache. The spans are in the order an agent finishes them, children first.
"""

import os
import time
import uuid

import grpc
from skywalking.protocol.common.Common_pb2 import KeyStringValuePair
from skywalking.protocol.language_agent.Tracing_pb2 import SegmentObject, SpanLayer, SpanObject, SpanType
from skywalking.protocol.language_agent.Tracing_pb2_grpc import TraceSegmentReportServiceStub

COLLECTOR = os.environ.get("SW_COLLECTOR", "oap:11800")
INTERVAL_S = float(os.environ.get("EMITTER_INTERVAL_S", "1"))
SERVICE = os.environ.get("EMITTER_SERVICE", "e2e-tal-native-svc")
INSTANCE = os.environ.get("EMITTER_INSTANCE", "e2e-tal-native-1")


def tags(**pairs):
    return [KeyStringValuePair(key=key.replace("_", "."), value=value) for key, value in pairs.items()]


def segment():
    start = int(time.time() * 1000) - 60
    entry = SpanObject(spanId=0, parentSpanId=-1, startTime=start, endTime=start + 50,
                       operationName="POST:/orders", spanType=SpanType.Entry, spanLayer=SpanLayer.Http,
                       componentId=1, tags=tags(http_method="POST", url="http://e2e-tal-native-svc/orders"))
    local = SpanObject(spanId=1, parentSpanId=0, startTime=start + 5, endTime=start + 45,
                       operationName="OrderService.place", spanType=SpanType.Local)
    database = SpanObject(spanId=2, parentSpanId=1, startTime=start + 10, endTime=start + 20,
                          operationName="Mysql/JDBC/PreparedStatement/execute", spanType=SpanType.Exit,
                          spanLayer=SpanLayer.Database, componentId=33, peer="mysql:3306",
                          tags=tags(db_type="Mysql", db_instance="orders",
                                    db_statement="INSERT INTO orders (id) VALUES (?)"))
    cache = SpanObject(spanId=3, parentSpanId=1, startTime=start + 25, endTime=start + 30,
                       operationName="Jedis/set", spanType=SpanType.Exit, spanLayer=SpanLayer.Cache,
                       componentId=30, peer="redis:6379",
                       tags=tags(cache_type="Redis", cache_cmd="SET", cache_op="write", cache_key="order:1"))
    return SegmentObject(traceId=uuid.uuid4().hex, traceSegmentId=uuid.uuid4().hex, service=SERVICE,
                         serviceInstance=INSTANCE, spans=[database, cache, local, entry])


def main():
    stub = TraceSegmentReportServiceStub(grpc.insecure_channel(COLLECTOR))
    while True:
        try:
            stub.collect(iter([segment()]))
        except grpc.RpcError as e:
            print("send failed: %s" % e.code(), flush=True)
        time.sleep(INTERVAL_S)


if __name__ == "__main__":
    main()
