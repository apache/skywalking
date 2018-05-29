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

package org.apache.skywalking.apm.collector.receiver.zipkin.provider.transform;

import java.util.List;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.RegisterServices;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.data.ZipkinTrace;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.handler.SpanJettyHandler;
import org.apache.skywalking.apm.network.proto.TraceSegmentObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Span;

/**
 * @author wusheng
 */
public class Zipkin2SkyWalkingTransfer {
    private static final Logger logger = LoggerFactory.getLogger(SpanJettyHandler.class);
    public static Zipkin2SkyWalkingTransfer INSTANCE = new Zipkin2SkyWalkingTransfer();
    private RegisterServices registerServices;

    private Zipkin2SkyWalkingTransfer() {
    }

    public void setRegisterServices(
        RegisterServices registerServices) {
        this.registerServices = registerServices;
    }

    public void transfer(ZipkinTrace trace) {
        List<Span> traceSpans = trace.getSpans();

        if (traceSpans.size() > 0) {
            try {
                List<TraceSegmentObject.Builder> builderList = SegmentBuilder.build(traceSpans, registerServices);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
