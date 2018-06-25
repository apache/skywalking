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

package org.apache.skywalking.apm.collector.agent.grpc.provider.handler;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.AnalysisSegmentParserModule;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.service.ISegmentParseService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.apache.skywalking.apm.network.proto.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class TraceSegmentServiceHandler extends TraceSegmentServiceGrpc.TraceSegmentServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(TraceSegmentServiceHandler.class);

    private final ISegmentParseService segmentParseService;
    private final Boolean debug;

    public TraceSegmentServiceHandler(ModuleManager moduleManager) {
        this.segmentParseService = moduleManager.find(AnalysisSegmentParserModule.NAME).getService(ISegmentParseService.class);
        this.debug = System.getProperty("debug") != null;
    }

    @Override public StreamObserver<UpstreamSegment> collect(StreamObserver<Downstream> responseObserver) {
        return new StreamObserver<UpstreamSegment>() {
            @Override public void onNext(UpstreamSegment segment) {
                if (logger.isDebugEnabled()) {
                    logger.debug("receive segment");
                }

                segmentParseService.parse(segment, ISegmentParseService.Source.Agent);

                if (debug) {
                    long count = SegmentCounter.INSTANCE.incrementAndGet();
                    if (count % 100000 == 0) {
                        logger.info("received segment count: {}", count);
                    }
                }
            }

            @Override public void onError(Throwable throwable) {
                logger.error(throwable.getMessage(), throwable);
                responseObserver.onCompleted();
            }

            @Override public void onCompleted() {
                responseObserver.onNext(Downstream.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
