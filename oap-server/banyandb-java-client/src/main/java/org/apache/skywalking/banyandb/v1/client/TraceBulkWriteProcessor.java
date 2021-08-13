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

package org.apache.skywalking.banyandb.v1.client;

import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.v1.trace.BanyandbTrace;
import org.apache.skywalking.banyandb.v1.trace.TraceServiceGrpc;

/**
 * TraceWriteProcessor works for trace flush.
 */
@Slf4j
public class TraceBulkWriteProcessor extends BulkWriteProcessor {
    /**
     * The instance name.
     */
    private final String group;
    private TraceServiceGrpc.TraceServiceStub traceServiceStub;

    /**
     * Create the processor.
     *
     * @param traceServiceStub stub for gRPC call.
     * @param maxBulkSize      the max bulk size for the flush operation
     * @param flushInterval    if given maxBulkSize is not reached in this period, the flush would be trigger
     *                         automatically. Unit is second.
     * @param concurrency      the number of concurrency would run for the flush max.
     */
    protected TraceBulkWriteProcessor(final String group,
                                      final TraceServiceGrpc.TraceServiceStub traceServiceStub,
                                      final int maxBulkSize,
                                      final int flushInterval,
                                      final int concurrency) {
        super("TraceBulkWriteProcessor", maxBulkSize, flushInterval, concurrency);
        this.group = group;
        this.traceServiceStub = traceServiceStub;
    }

    @Override
    protected void flush(final List data) {
        final StreamObserver<BanyandbTrace.WriteRequest> writeRequestStreamObserver
            = traceServiceStub.withDeadlineAfter(
                                  flushInterval, TimeUnit.SECONDS)
                              .write(
                                  new StreamObserver<BanyandbTrace.WriteResponse>() {
                                      @Override
                                      public void onNext(
                                          BanyandbTrace.WriteResponse writeResponse) {
                                      }

                                      @Override
                                      public void onError(
                                          Throwable throwable) {
                                          log.error(
                                              "Error occurs in flushing traces.",
                                              throwable
                                          );
                                      }

                                      @Override
                                      public void onCompleted() {
                                      }
                                  });
        try {
            data.forEach(write -> {
                final TraceWrite traceWrite = (TraceWrite) write;
                BanyandbTrace.WriteRequest request = traceWrite.build(group);
                writeRequestStreamObserver.onNext(request);
            });
        } finally {
            writeRequestStreamObserver.onCompleted();
        }
    }
}
