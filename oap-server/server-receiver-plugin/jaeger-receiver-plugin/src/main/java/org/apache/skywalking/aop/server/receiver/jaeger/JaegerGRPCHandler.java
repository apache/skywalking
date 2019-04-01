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

package org.apache.skywalking.aop.server.receiver.jaeger;

import io.grpc.stub.StreamObserver;
import io.jaegertracing.api_v2.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

/**
 * @author wusheng
 */
public class JaegerGRPCHandler extends CollectorServiceGrpc.CollectorServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(JaegerGRPCHandler.class);

    public JaegerGRPCHandler(ModuleManager manager) {

    }

    public void postSpans(Collector.PostSpansRequest request,
        StreamObserver<Collector.PostSpansResponse> responseObserver) {

        request.getBatch().getSpansList().forEach(span -> {
            logger.debug(span.toString());
        });

        responseObserver.onNext(Collector.PostSpansResponse.newBuilder().build());
        responseObserver.onCompleted();
    }
}
