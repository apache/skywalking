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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.bulk;

import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.measure.v1.BanyandbMeasure;
import org.apache.skywalking.banyandb.measure.v1.MeasureServiceGrpc;
import org.apache.skywalking.library.banyandb.v1.client.AbstractWrite;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class AbstractBulkWriteProcessorTest {
    @ParameterizedTest
    @CsvSource({"0, false", "1, false", "0, true", "1, true"})
    public void buildFailureCompletesEveryUnsentRequest(int failedIndex, boolean failingToString) {
        final IllegalArgumentException buildFailure = new IllegalArgumentException("Invalid write");
        final List<AbstractBulkWriteProcessor.Holder> requests = new ArrayList<>();
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            futures.add(future);
            requests.add(AbstractBulkWriteProcessor.Holder.create(
                new TestWrite(i == failedIndex ? buildFailure : null, failingToString), future));
        }
        final CompletableFuture<Void> allRequests = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        try (TestBulkWriteProcessor processor = new TestBulkWriteProcessor()) {
            final CompletableFuture<Void> response = processor.doObservedFlush(requests);
            assertFalse(response.isDone());
            assertEquals(failedIndex, processor.sentRequests.size(), "Only writes before the failure should be sent");
            assertTrue(processor.requestStreamCompleted);

            response.complete(null);
            assertTrue(allRequests.isDone(), "A build failure must not leave the persistence round waiting forever");
            assertSame(buildFailure, assertThrows(CompletionException.class, allRequests::join).getCause());
            for (int i = 0; i < futures.size(); i++) {
                if (i < failedIndex) {
                    assertNull(futures.get(i).join(), "Already sent writes keep their successful completion");
                } else {
                    assertSame(buildFailure, assertThrows(CompletionException.class, futures.get(i)::join).getCause());
                }
            }
        }
    }

    private static class TestWrite extends AbstractWrite<BanyandbMeasure.WriteRequest> {
        private final RuntimeException buildFailure;
        private final boolean failingToString;

        private TestWrite(RuntimeException buildFailure, boolean failingToString) {
            super(BanyandbCommon.Metadata.newBuilder().setGroup("group").setName("measure").build(), 1);
            this.buildFailure = buildFailure;
            this.failingToString = failingToString;
        }

        @Override
        protected BanyandbMeasure.WriteRequest build(BanyandbCommon.Metadata metadata) {
            return buildValues();
        }

        @Override
        protected BanyandbMeasure.WriteRequest buildValues() {
            if (buildFailure != null) {
                throw buildFailure;
            }
            return BanyandbMeasure.WriteRequest.getDefaultInstance();
        }

        @Override
        public String toString() {
            // MeasureWrite.toString serializes values too, so logging a malformed value can fail again.
            if (failingToString && buildFailure != null) {
                throw buildFailure;
            }
            return super.toString();
        }
    }

    private static class TestBulkWriteProcessor extends AbstractBulkWriteProcessor<BanyandbMeasure.WriteRequest,
        MeasureServiceGrpc.MeasureServiceStub> {
        private final List<BanyandbMeasure.WriteRequest> sentRequests = new ArrayList<>();
        private boolean requestStreamCompleted;

        private TestBulkWriteProcessor() {
            super(MeasureServiceGrpc.newStub(mock(Channel.class)), "test", 10, 60, 1, 10);
            // This test drives doFlush directly, so no background flush is needed.
            close();
        }

        @Override
        protected CompletableFuture<Void> doObservedFlush(List<Holder> data) {
            return doFlush(data, new HistogramMetrics() {
                @Override
                public void observe(double value) {
                }
            }.createTimer());
        }

        @Override
        protected StreamObserver<BanyandbMeasure.WriteRequest> buildStreamObserver(
            MeasureServiceGrpc.MeasureServiceStub stub, CompletableFuture<Void> batch) {
            return new StreamObserver<>() {
                @Override
                public void onNext(BanyandbMeasure.WriteRequest request) {
                    sentRequests.add(request);
                }

                @Override
                public void onError(Throwable t) {
                    batch.completeExceptionally(t);
                }

                @Override
                public void onCompleted() {
                    requestStreamCompleted = true;
                }
            };
        }
    }
}
