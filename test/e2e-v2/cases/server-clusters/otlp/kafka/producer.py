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

# Publishes to the `orders` topic every second through the bootstrap server named by BOOTSTRAP_SERVERS. Run under
# opentelemetry-instrument, whose confluent-kafka instrumentation reports the first bootstrap entry as the PRODUCER
# span's server.address and server.port.

import os
import time

from confluent_kafka import Producer

producer = Producer({"bootstrap.servers": os.environ["BOOTSTRAP_SERVERS"]})
while True:
    producer.produce("orders", b"order")
    producer.poll(0)
    time.sleep(1)
