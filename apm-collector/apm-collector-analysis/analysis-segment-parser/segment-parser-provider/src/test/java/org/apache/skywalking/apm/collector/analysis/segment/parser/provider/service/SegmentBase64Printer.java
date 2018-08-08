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

package org.apache.skywalking.apm.collector.analysis.segment.parser.provider.service;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.*;
import org.apache.skywalking.apm.network.language.agent.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class SegmentBase64Printer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SegmentBase64Printer.class);

    public static void main(String[] args) throws InvalidProtocolBufferException {
        if (args.length == 0) {
            return;
        }

        byte[] binarySegment = Base64.getDecoder().decode(args[0]);
        TraceSegmentObject segmentObject = TraceSegmentObject.parseFrom(binarySegment);

        UniqueId segmentId = segmentObject.getTraceSegmentId();
        StringBuilder segmentIdBuilder = new StringBuilder();
        for (int i = 0; i < segmentId.getIdPartsList().size(); i++) {
            if (i == 0) {
                segmentIdBuilder.append(segmentId.getIdPartsList().get(i));
            } else {
                segmentIdBuilder.append(".").append(segmentId.getIdPartsList().get(i));
            }
        }
        LOGGER.info("SegmentId: {}", segmentIdBuilder.toString());
        LOGGER.info("ApplicationId: {}", segmentObject.getApplicationId());
        LOGGER.info("ApplicationInstanceId: {}", segmentObject.getApplicationInstanceId());
        List<SpanObject> spansList = segmentObject.getSpansList();
        LOGGER.info("Spans:");
        spansList.forEach(span -> {
            LOGGER.info("   Span:");
            LOGGER.info("       SpanId: {}", span.getSpanId());
            LOGGER.info("       ParentSpanId: {}", span.getParentSpanId());
            LOGGER.info("       SpanLayer: {}", span.getSpanLayer());
            LOGGER.info("       SpanType: {}", span.getSpanType());
            LOGGER.info("       StartTime: {}", span.getStartTime());
            LOGGER.info("       EndTime: {}", span.getEndTime());
            LOGGER.info("       ComponentId: {}", span.getComponentId());
            LOGGER.info("       Component: {}", span.getComponent());
            LOGGER.info("       OperationNameId: {}", span.getOperationNameId());
            LOGGER.info("       OperationName: {}", span.getOperationName());
            LOGGER.info("       PeerId: {}", span.getPeerId());
            LOGGER.info("       Peer: {}", span.getPeer());
            LOGGER.info("       IsError: {}", span.getIsError());

            LOGGER.info("       reference:");
            span.getRefsList().forEach(reference -> {
                LOGGER.info("           EntryApplicationInstanceId: {}", reference.getEntryApplicationInstanceId());
                LOGGER.info("           EntryServiceId: {}", reference.getEntryServiceId());
                LOGGER.info("           EntryServiceName: {}", reference.getEntryServiceName());
                LOGGER.info("           ParentTraceSegmentId: {}", reference.getParentTraceSegmentId());
                LOGGER.info("           ParentSpanId: {}", reference.getParentSpanId());
                LOGGER.info("           ParentApplicationInstanceId: {}", reference.getParentApplicationInstanceId());
                LOGGER.info("           ParentServiceId: {}", reference.getParentServiceId());
                LOGGER.info("           ParentServiceName: {}", reference.getParentServiceName());
                LOGGER.info("           NetworkAddressId: {}", reference.getNetworkAddressId());
                LOGGER.info("           NetworkAddress: {}", reference.getNetworkAddress());
            });
        });
    }
}
