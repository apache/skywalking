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
 */

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.trace.v1.Span;
import org.junit.jupiter.api.Test;

class OTLPSpanReaderImplTest {
    @Test
    void shouldEncodeIdsAsFixedWidthLowercaseHex() {
        final Span span = Span.newBuilder()
                              .setTraceId(ByteString.copyFrom(new byte[] {
                                  0x0a, (byte) 0xf7, 0x65, 0x19,
                                  0x16, (byte) 0xcd, 0x43, (byte) 0xdd,
                                  (byte) 0x84, 0x48, (byte) 0xeb, 0x21,
                                  0x1c, (byte) 0x80, 0x31, (byte) 0x9c
                              }))
                              .setSpanId(ByteString.copyFrom(new byte[] {
                                  0x00, (byte) 0xb7, (byte) 0xad, 0x6b,
                                  0x71, 0x69, 0x20, 0x33
                              }))
                              .build();

        final OTLPSpanReaderImpl reader = new OTLPSpanReaderImpl(span);

        assertEquals("0af7651916cd43dd8448eb211c80319c", reader.traceId());
        assertEquals("00b7ad6b71692033", reader.spanId());
    }

    @Test
    void shouldReturnEmptyIdsWhenUnset() {
        final OTLPSpanReaderImpl reader = new OTLPSpanReaderImpl(Span.getDefaultInstance());

        assertEquals("", reader.traceId());
        assertEquals("", reader.spanId());
    }
}
