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

package org.apache.skywalking.oap.server.receiver.trace.provider.handler.v8.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.SpanAttachedEvent;
import org.apache.skywalking.apm.network.language.agent.v3.SpanAttachedEventReportServiceGrpc;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;

import java.util.concurrent.TimeUnit;

@Slf4j
public class SpanAttachedEventReportServiceHandler extends SpanAttachedEventReportServiceGrpc.SpanAttachedEventReportServiceImplBase implements GRPCHandler {
    public SpanAttachedEventReportServiceHandler(ModuleManager moduleManager) {
    }

    @Override
    public StreamObserver<SpanAttachedEvent> collect(StreamObserver<Commands> responseObserver) {
        return new StreamObserver<SpanAttachedEvent>() {
            @Override
            public void onNext(SpanAttachedEvent event) {
                if (log.isDebugEnabled()) {
                    log.debug("receive span attached event is streaming");
                }

                final SpanAttachedEventRecord record = new SpanAttachedEventRecord();
                record.setStartTimeSecond(event.getStartTime().getSeconds());
                record.setStartTimeNanos(event.getStartTime().getNanos());
                record.setEvent(event.getEvent());
                record.setEndTimeSecond(event.getEndTime().getSeconds());
                record.setEndTimeNanos(event.getEndTime().getNanos());
                record.setTraceRefType(event.getTraceContext().getTypeValue());
                record.setTraceId(event.getTraceContext().getTraceId());
                record.setTraceSegmentId(event.getTraceContext().getTraceSegmentId());
                record.setTraceSpanId(event.getTraceContext().getSpanId());
                record.setDataBinary(event.toByteArray());
                record.setTimeBucket(TimeBucket.getMinuteTimeBucket(TimeUnit.SECONDS.toMillis(record.getStartTimeSecond())
                    + TimeUnit.NANOSECONDS.toMillis(record.getStartTimeNanos())));

                RecordStreamProcessor.getInstance().in(record);
            }

            @Override
            public void onError(Throwable throwable) {
                Status status = Status.fromThrowable(throwable);
                if (Status.CANCELLED.getCode() == status.getCode()) {
                    if (log.isDebugEnabled()) {
                        log.debug(throwable.getMessage(), throwable);
                    }
                    return;
                }
                log.error(throwable.getMessage(), throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(Commands.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}