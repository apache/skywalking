/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentstream.grpc.handler;

import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.agentstream.worker.segment.SegmentParse;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.TraceSegmentServiceGrpc;
import org.skywalking.apm.network.proto.UpstreamSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class TraceSegmentServiceHandler extends TraceSegmentServiceGrpc.TraceSegmentServiceImplBase implements GRPCHandler {

    private final Logger logger = LoggerFactory.getLogger(TraceSegmentServiceHandler.class);

    @Override public StreamObserver<UpstreamSegment> collect(StreamObserver<Downstream> responseObserver) {
        return new StreamObserver<UpstreamSegment>() {
            @Override public void onNext(UpstreamSegment segment) {
                logger.debug("receive segment");
                SegmentParse segmentParse = new SegmentParse();
                segmentParse.parse(segment, SegmentParse.Source.Agent);
            }

            @Override public void onError(Throwable throwable) {
                logger.error(throwable.getMessage(), throwable);
            }

            @Override public void onCompleted() {
                responseObserver.onNext(Downstream.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
