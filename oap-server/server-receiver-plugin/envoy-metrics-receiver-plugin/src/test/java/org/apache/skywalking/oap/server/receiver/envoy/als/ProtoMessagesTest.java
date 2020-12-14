/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.receiver.envoy.als;

import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsMessage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProtoMessagesTest {

    @Test
    @SuppressWarnings("ConstantConditions")
    public void testGetField() throws IOException {
        try (final InputStreamReader isr = new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream("envoy-ingress.msg"))) {
            final StreamAccessLogsMessage.Builder requestBuilder = StreamAccessLogsMessage.newBuilder();
            JsonFormat.parser().merge(isr, requestBuilder);

            final StreamAccessLogsMessage message = requestBuilder.build();

            assertTrue(ProtoMessages.findField(message, "identifier").isPresent());
            assertTrue(ProtoMessages.findField(message, "identifier.node").isPresent());
            assertEquals(ProtoMessages.findField(message, "identifier.log_name", null), "als");
            assertEquals(ProtoMessages.findField(message, "identifier.node.id", null), "router~10.44.2.56~istio-ingressgateway-699c7dc774-hjxq5.istio-system~istio-system.svc.cluster.local");

            assertTrue(ProtoMessages.findField(message, "tcp_logs.log_entry", (List<Message>) null).isEmpty());
            assertTrue(ProtoMessages.findField(message, "http_logs.log_entry").isPresent());

            final List<Message> logs = ProtoMessages.findField(message, "http_logs.log_entry", null);
            assertFalse(logs.isEmpty());

            assertEquals(ProtoMessages.findField(logs.get(0), "common_properties.downstream_remote_address.socket_address.address", ""), "10.138.0.14");

            final Timestamp ts = ProtoMessages.findField(logs.get(0), "common_properties.start_time", null);
            assertTrue(ts.getNanos() > 0);
        }
    }

}
