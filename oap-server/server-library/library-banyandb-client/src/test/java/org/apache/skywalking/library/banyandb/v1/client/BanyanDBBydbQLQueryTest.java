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

package org.apache.skywalking.library.banyandb.v1.client;

import io.grpc.stub.StreamObserver;
import java.io.IOException;
import org.apache.skywalking.banyandb.bydbql.v1.BanyandbBydbql;
import org.apache.skywalking.banyandb.bydbql.v1.BydbQLServiceGrpc;
import org.apache.skywalking.banyandb.measure.v1.BanyandbMeasure;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty;
import org.apache.skywalking.banyandb.stream.v1.BanyandbStream;
import org.apache.skywalking.banyandb.trace.v1.BanyandbTrace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BanyanDBBydbQLQueryTest extends AbstractBanyanDBClientTest {

    // The last request the fake service received, for asserting query text and params.
    private BanyandbBydbql.QueryRequest lastRequest;

    // The oneof branch the fake service returns for the next call.
    private BanyandbBydbql.QueryResponse nextResponse;

    @BeforeEach
    public void setUp() throws IOException {
        super.setUp(bindService(new BydbQLServiceGrpc.BydbQLServiceImplBase() {
            @Override
            public void query(BanyandbBydbql.QueryRequest request,
                              StreamObserver<BanyandbBydbql.QueryResponse> responseObserver) {
                lastRequest = request;
                responseObserver.onNext(nextResponse);
                responseObserver.onCompleted();
            }
        }));
    }

    @AfterEach
    public void tearDown() {
        if (this.channel != null) {
            this.channel.shutdownNow();
        }
    }

    @Test
    public void queryMeasure_sendsQueryAndParams_andUnwrapsMeasureResult() throws Exception {
        nextResponse = BanyandbBydbql.QueryResponse.newBuilder()
                .setMeasureResult(BanyandbMeasure.QueryResponse.newBuilder()
                        .addDataPoints(BanyandbMeasure.DataPoint.newBuilder().build())
                        .build())
                .build();

        MeasureQueryResponse resp = client.queryMeasure(
                "SELECT x FROM MEASURE m IN g TIME BETWEEN ? AND ? WHERE id = ?",
                Value.timestampTagValue(1000L), Value.timestampTagValue(2000L), Value.stringTagValue("svc"));

        assertEquals(1, resp.size());
        assertEquals("SELECT x FROM MEASURE m IN g TIME BETWEEN ? AND ? WHERE id = ?", lastRequest.getQuery());
        assertEquals(3, lastRequest.getParamsCount());
        assertEquals("svc", lastRequest.getParams(2).getStr().getValue());
    }

    @Test
    public void queryMeasure_throwsOnResultCaseMismatch() {
        nextResponse = BanyandbBydbql.QueryResponse.newBuilder()
                .setStreamResult(BanyandbStream.QueryResponse.newBuilder().build())
                .build();

        assertThrows(IllegalStateException.class,
                () -> client.queryMeasure("SELECT x FROM MEASURE m IN g TIME > ?", Value.timestampTagValue(1000L)));
    }

    @Test
    public void queryStream_unwrapsStreamResult() throws Exception {
        nextResponse = BanyandbBydbql.QueryResponse.newBuilder()
                .setStreamResult(BanyandbStream.QueryResponse.newBuilder()
                        .addElements(BanyandbStream.Element.newBuilder().build())
                        .build())
                .build();

        StreamQueryResponse resp = client.queryStream(
                "SELECT trace_id FROM STREAM s IN g TIME > ?", Value.timestampTagValue(1000L));

        assertNotNull(resp);
        assertEquals("SELECT trace_id FROM STREAM s IN g TIME > ?", lastRequest.getQuery());
        assertEquals(1, lastRequest.getParamsCount());
    }

    @Test
    public void queryTrace_unwrapsTraceResult() throws Exception {
        nextResponse = BanyandbBydbql.QueryResponse.newBuilder()
                .setTraceResult(BanyandbTrace.QueryResponse.newBuilder().build())
                .build();

        TraceQueryResponse resp = client.queryTrace(
                "SELECT () FROM TRACE t IN g TIME > ?", Value.timestampTagValue(1000L));

        assertNotNull(resp);
        assertEquals("SELECT () FROM TRACE t IN g TIME > ?", lastRequest.getQuery());
    }

    @Test
    public void queryTopN_unwrapsTopNResult() throws Exception {
        nextResponse = BanyandbBydbql.QueryResponse.newBuilder()
                .setTopnResult(BanyandbMeasure.TopNResponse.newBuilder().build())
                .build();

        TopNQueryResponse resp = client.queryTopN(
                "SHOW TOP ? FROM MEASURE m IN g TIME > ? ORDER BY DESC",
                Value.longTagValue(10L), Value.timestampTagValue(1000L));

        assertNotNull(resp);
        assertEquals(2, lastRequest.getParamsCount());
    }

    @Test
    public void queryProperty_returnsRawPropertyResponse() throws Exception {
        nextResponse = BanyandbBydbql.QueryResponse.newBuilder()
                .setPropertyResult(BanyandbProperty.QueryResponse.newBuilder().build())
                .build();

        BanyandbProperty.QueryResponse resp = client.queryProperty(
                "SELECT ip FROM PROPERTY p IN g WHERE ID = ?", Value.stringTagValue("k"));

        assertNotNull(resp);
        assertEquals("SELECT ip FROM PROPERTY p IN g WHERE ID = ?", lastRequest.getQuery());
    }
}
