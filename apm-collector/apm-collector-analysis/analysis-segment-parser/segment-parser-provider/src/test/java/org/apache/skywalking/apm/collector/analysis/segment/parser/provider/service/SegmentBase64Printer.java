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
import java.util.Base64;
import java.util.List;
import org.apache.skywalking.apm.network.proto.SpanObject;
import org.apache.skywalking.apm.network.proto.TraceSegmentObject;
import org.apache.skywalking.apm.network.proto.UniqueId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentBase64Printer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SegmentBase64Printer.class);

    public static void main(String[] args) throws InvalidProtocolBufferException {
        String segmentBase64 = "CgoKCJbf2NPCLBAQEiAQ////////////ARiV39jTwiwg2+7Y08IsMNQPWANgARIlCAEYn9/Y08IsILns2NPCLDCUyAJA////////////AVABWAJgAxInCAIQARif39jTwiwguezY08IsMJTIAkD///////////8BUAFYAmADEicIAxACGJ/f2NPCLCC57NjTwiwwlMgCQP///////////wFQAVgCYAMSJwgEEAMYn9/Y08IsILns2NPCLDCUyAJA////////////AVABWAJgAxInCAUQBBif39jTwiwguezY08IsMJTIAkD///////////8BUAFYAmADEicIBhAFGJ/f2NPCLCC57NjTwiwwlMgCQP///////////wFQAVgCYAMSJwgHEAYYn9/Y08IsILns2NPCLDCUyAJA////////////AVABWAJgAxInCAgQBxif39jTwiwguezY08IsMJTIAkD///////////8BUAFYAmADEicICRAIGJ/f2NPCLCC57NjTwiwwlMgCQP///////////wFQAVgCYAMSJwgKEAkYn9/Y08IsILns2NPCLDCUyAJA////////////AVABWAJgAxInCAsQChif39jTwiwguezY08IsMJTIAkD///////////8BUAFYAmADEicIDBALGJ/f2NPCLCC57NjTwiwwlMgCQP///////////wFQAVgCYAMSJwgNEAwYn9/Y08IsILns2NPCLDCUyAJA////////////AVABWAJgAxInCA4QDRif39jTwiwguezY08IsMJTIAkD///////////8BUAFYAmADEvMCCA8QDhif39jTwiwguezY08IsMJTIAkD///////////8BUAFYAmADggHIAhKhAQoNZXJyb3IgbWVzc2FnZRKPAVtJTkZPXSBCdWlsZGluZyBqYXI6IC9Vc2Vycy9wZW5neXM1L2NvZGUvc2t5LXdhbGtpbmcvY29sbGVjdG9yLXBlcmZvcm1hbmNlLXRlc3QvdGFyZ2V0L2NvbGxlY3Rvci1wZXJmb3JtYW5jZS10ZXN0LTEuMC1qYXItd2l0aC1kZXBlbmRlbmNpZXMuamFyEqEBCg1lcnJvciBtZXNzYWdlEo8BW0lORk9dIEJ1aWxkaW5nIGphcjogL1VzZXJzL3Blbmd5czUvY29kZS9za3ktd2Fsa2luZy9jb2xsZWN0b3ItcGVyZm9ybWFuY2UtdGVzdC90YXJnZXQvY29sbGVjdG9yLXBlcmZvcm1hbmNlLXRlc3QtMS4wLWphci13aXRoLWRlcGVuZGVuY2llcy5qYXIS8wIIEBAPGJ/f2NPCLCC57NjTwiwwlMgCQP///////////wFQAVgCYAOCAcgCEqEBCg1lcnJvciBtZXNzYWdlEo8BW0lORk9dIEJ1aWxkaW5nIGphcjogL1VzZXJzL3Blbmd5czUvY29kZS9za3ktd2Fsa2luZy9jb2xsZWN0b3ItcGVyZm9ybWFuY2UtdGVzdC90YXJnZXQvY29sbGVjdG9yLXBlcmZvcm1hbmNlLXRlc3QtMS4wLWphci13aXRoLWRlcGVuZGVuY2llcy5qYXISoQEKDWVycm9yIG1lc3NhZ2USjwFbSU5GT10gQnVpbGRpbmcgamFyOiAvVXNlcnMvcGVuZ3lzNS9jb2RlL3NreS13YWxraW5nL2NvbGxlY3Rvci1wZXJmb3JtYW5jZS10ZXN0L3RhcmdldC9jb2xsZWN0b3ItcGVyZm9ybWFuY2UtdGVzdC0xLjAtamFyLXdpdGgtZGVwZW5kZW5jaWVzLmphchLzAggREBAYn9/Y08IsILns2NPCLDCUyAJA////////////AVABWAJgA4IByAISoQEKDWVycm9yIG1lc3NhZ2USjwFbSU5GT10gQnVpbGRpbmcgamFyOiAvVXNlcnMvcGVuZ3lzNS9jb2RlL3NreS13YWxraW5nL2NvbGxlY3Rvci1wZXJmb3JtYW5jZS10ZXN0L3RhcmdldC9jb2xsZWN0b3ItcGVyZm9ybWFuY2UtdGVzdC0xLjAtamFyLXdpdGgtZGVwZW5kZW5jaWVzLmphchKhAQoNZXJyb3IgbWVzc2FnZRKPAVtJTkZPXSBCdWlsZGluZyBqYXI6IC9Vc2Vycy9wZW5neXM1L2NvZGUvc2t5LXdhbGtpbmcvY29sbGVjdG9yLXBlcmZvcm1hbmNlLXRlc3QvdGFyZ2V0L2NvbGxlY3Rvci1wZXJmb3JtYW5jZS10ZXN0LTEuMC1qYXItd2l0aC1kZXBlbmRlbmNpZXMuamFyEvMCCBIQERif39jTwiwguezY08IsMJTIAkD///////////8BUAFYAmADggHIAhKhAQoNZXJyb3IgbWVzc2FnZRKPAVtJTkZPXSBCdWlsZGluZyBqYXI6IC9Vc2Vycy9wZW5neXM1L2NvZGUvc2t5LXdhbGtpbmcvY29sbGVjdG9yLXBlcmZvcm1hbmNlLXRlc3QvdGFyZ2V0L2NvbGxlY3Rvci1wZXJmb3JtYW5jZS10ZXN0LTEuMC1qYXItd2l0aC1kZXBlbmRlbmNpZXMuamFyEqEBCg1lcnJvciBtZXNzYWdlEo8BW0lORk9dIEJ1aWxkaW5nIGphcjogL1VzZXJzL3Blbmd5czUvY29kZS9za3ktd2Fsa2luZy9jb2xsZWN0b3ItcGVyZm9ybWFuY2UtdGVzdC90YXJnZXQvY29sbGVjdG9yLXBlcmZvcm1hbmNlLXRlc3QtMS4wLWphci13aXRoLWRlcGVuZGVuY2llcy5qYXIS8wIIExASGJ/f2NPCLCC57NjTwiwwlMgCQP///////////wFQAVgCYAOCAcgCEqEBCg1lcnJvciBtZXNzYWdlEo8BW0lORk9dIEJ1aWxkaW5nIGphcjogL1VzZXJzL3Blbmd5czUvY29kZS9za3ktd2Fsa2luZy9jb2xsZWN0b3ItcGVyZm9ybWFuY2UtdGVzdC90YXJnZXQvY29sbGVjdG9yLXBlcmZvcm1hbmNlLXRlc3QtMS4wLWphci13aXRoLWRlcGVuZGVuY2llcy5qYXISoQEKDWVycm9yIG1lc3NhZ2USjwFbSU5GT10gQnVpbGRpbmcgamFyOiAvVXNlcnMvcGVuZ3lzNS9jb2RlL3NreS13YWxraW5nL2NvbGxlY3Rvci1wZXJmb3JtYW5jZS10ZXN0L3RhcmdldC9jb2xsZWN0b3ItcGVyZm9ybWFuY2UtdGVzdC0xLjAtamFyLXdpdGgtZGVwZW5kZW5jaWVzLmphchLzAggUEBMYn9/Y08IsILns2NPCLDCUyAJA////////////AVABWAJgA4IByAISoQEKDWVycm9yIG1lc3NhZ2USjwFbSU5GT10gQnVpbGRpbmcgamFyOiAvVXNlcnMvcGVuZ3lzNS9jb2RlL3NreS13YWxraW5nL2NvbGxlY3Rvci1wZXJmb3JtYW5jZS10ZXN0L3RhcmdldC9jb2xsZWN0b3ItcGVyZm9ybWFuY2UtdGVzdC0xLjAtamFyLXdpdGgtZGVwZW5kZW5jaWVzLmphchKhAQoNZXJyb3IgbWVzc2FnZRKPAVtJTkZPXSBCdWlsZGluZyBqYXI6IC9Vc2Vycy9wZW5neXM1L2NvZGUvc2t5LXdhbGtpbmcvY29sbGVjdG9yLXBlcmZvcm1hbmNlLXRlc3QvdGFyZ2V0L2NvbGxlY3Rvci1wZXJmb3JtYW5jZS10ZXN0LTEuMC1qYXItd2l0aC1kZXBlbmRlbmNpZXMuamFyGP7//////////wEgCA==";
        byte[] binarySegment = Base64.getDecoder().decode(segmentBase64);
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
