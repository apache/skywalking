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

# Consumes the `orders` topic through the bootstrap server named by BOOTSTRAP_SERVERS. Run under opentelemetry-instrument,
# whose confluent-kafka instrumentation reports a CONSUMER `process` span per message with the first bootstrap entry as
# its server.address and server.port.

import os

from confluent_kafka import Consumer

consumer = Consumer({
    "bootstrap.servers": os.environ["BOOTSTRAP_SERVERS"],
    "group.id": "orders-consumer",
    "auto.offset.reset": "earliest",
})
consumer.subscribe(["orders"])
while True:
    consumer.poll(1.0)
