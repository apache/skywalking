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

package org.apache.skywalking.apm.collector.agent.grpc.provider.handler;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.service.ISegmentParseService;
import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.network.proto.Downstream;
import org.apache.skywalking.apm.network.proto.UpstreamSegment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceSegmentServiceHandlerTest {

    private TraceSegmentServiceHandler traceSegmentServiceHandler;

    @Mock
    private ISegmentParseService segmentParseService;

    @Before
    public void setUp() {
        System.setProperty("debug", "true");
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        traceSegmentServiceHandler = new TraceSegmentServiceHandler(moduleManager);
        Whitebox.setInternalState(traceSegmentServiceHandler, "segmentParseService", segmentParseService);

    }

    @Test
    public void collect() {
        StreamObserver<UpstreamSegment> upstreamSegmentStreamObserver = traceSegmentServiceHandler.collect(new StreamObserver<Downstream>() {
            @Override
            public void onNext(Downstream downstream) {
                assertTrue(downstream.isInitialized());
            }

            @Override
            public void onError(Throwable throwable) {
                assertTrue(throwable instanceof IllegalArgumentException);
            }

            @Override
            public void onCompleted() {


            }
        });
        UpstreamSegment upstreamSegment = UpstreamSegment.newBuilder().build();
        upstreamSegmentStreamObserver.onNext(upstreamSegment);
        upstreamSegmentStreamObserver.onError(new IllegalArgumentException("exception"));
        upstreamSegmentStreamObserver.onCompleted();
    }
}