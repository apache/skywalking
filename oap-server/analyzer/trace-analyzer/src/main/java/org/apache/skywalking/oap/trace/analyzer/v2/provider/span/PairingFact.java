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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span;

import com.google.common.hash.Hashing;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;

/**
 * One half of a call, as {@link SpanPairing} needs it: the SERVER side carries the callee's identity under its
 * parent span id, the CLIENT side carries the address it dialed under its own span id. Routed to the node that
 * owns the trace by {@link #remoteHashCode()}, so both halves of every call meet on one node.
 */
@Getter
public class PairingFact extends StreamData {
    private String traceId = "";
    private String spanId = "";
    private String parentSpanId = "";
    private String service = "";
    private String instance = "";
    private String address = "";
    private boolean server;
    private boolean normal;
    private long timestampMillis;

    public PairingFact() {
    }

    /**
     * @param meta   a SERVER or CONSUMER span with a parent
     * @param normal the {@code normal} flag its service id carries
     * @return the callee half of the call
     */
    public static PairingFact server(final SpanMetadata meta, final boolean normal) {
        final PairingFact fact = new PairingFact();
        fact.traceId = meta.getTraceId();
        fact.spanId = meta.getSpanId();
        fact.parentSpanId = meta.getParentSpanId();
        fact.service = meta.getService();
        fact.instance = meta.getServiceInstance() == null ? "" : meta.getServiceInstance();
        fact.server = true;
        fact.normal = normal;
        fact.timestampMillis = meta.getTimestamp();
        return fact;
    }

    /**
     * @param meta    a CLIENT span
     * @param address the peer nothing else could name
     * @return the caller half of the call
     */
    public static PairingFact client(final SpanMetadata meta, final String address) {
        final PairingFact fact = new PairingFact();
        fact.traceId = meta.getTraceId();
        fact.spanId = meta.getSpanId();
        fact.parentSpanId = meta.getParentSpanId() == null ? "" : meta.getParentSpanId();
        fact.service = meta.getService();
        fact.address = address;
        fact.timestampMillis = meta.getTimestamp();
        return fact;
    }

    /**
     * Every fact of one trace hashes alike, which is what puts both halves of a call on one node.
     */
    @Override
    public int remoteHashCode() {
        return traceId.hashCode();
    }

    /**
     * A bucket from 0 to 99 for sampling, from a different hash of the trace id than {@link #remoteHashCode()}:
     * with one hash, the residues that pass the sample and the residue that picks the owner move together, and a
     * cluster whose size shares a factor with 100 would hand every sampled trace to a subset of its nodes; at 50
     * nodes and 25 percent, to the first 25. Both halves of a call still sample alike, since they share the id.
     *
     * @return the bucket this fact's trace falls in
     */
    public int sampleBucket() {
        return Math.abs(Hashing.murmur3_32_fixed().hashUnencodedChars(traceId).asInt() % 100);
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(traceId);
        builder.addDataStrings(spanId);
        builder.addDataStrings(parentSpanId);
        builder.addDataStrings(service);
        builder.addDataStrings(instance);
        builder.addDataStrings(address);
        builder.addDataIntegers(server ? 1 : 0);
        builder.addDataIntegers(normal ? 1 : 0);
        builder.addDataLongs(timestampMillis);
        return builder;
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        traceId = remoteData.getDataStrings(0);
        spanId = remoteData.getDataStrings(1);
        parentSpanId = remoteData.getDataStrings(2);
        service = remoteData.getDataStrings(3);
        instance = remoteData.getDataStrings(4);
        address = remoteData.getDataStrings(5);
        server = remoteData.getDataIntegers(0) == 1;
        normal = remoteData.getDataIntegers(1) == 1;
        timestampMillis = remoteData.getDataLongs(0);
    }
}
