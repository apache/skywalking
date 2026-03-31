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

package org.apache.skywalking.oap.query.traceql.converter;

import io.grafana.tempo.tempopb.Trace;
import io.grafana.tempo.tempopb.TraceByIDResponse;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.skywalking.oap.query.traceql.entity.OtlpTraceResponse;

public class OTLPConverter {
    /**
     * Convert Protobuf TraceByIDResponse to OtlpTraceResponse JSON model.
     *
     * @param protoResponse TraceByIDResponse in Protobuf format
     * @return OtlpTraceResponse JSON model
     */
    public static OtlpTraceResponse convertProtobufToJson(TraceByIDResponse protoResponse, TraceType traceType) {
        OtlpTraceResponse response = new OtlpTraceResponse();
        OtlpTraceResponse.TraceData traceData = new OtlpTraceResponse.TraceData();

        Trace trace = protoResponse.getTrace();

        // Convert each ResourceSpans
        for (ResourceSpans rs : trace.getResourceSpansList()) {
            OtlpTraceResponse.ResourceSpans jsonResourceSpans = new OtlpTraceResponse.ResourceSpans();

            // Convert Resource
            OtlpTraceResponse.Resource jsonResource = new OtlpTraceResponse.Resource();
            for (KeyValue attr : rs.getResource().getAttributesList()) {
                OtlpTraceResponse.KeyValue jsonKv = new OtlpTraceResponse.KeyValue();
                jsonKv.setKey(attr.getKey());
                OtlpTraceResponse.AnyValue jsonValue = new OtlpTraceResponse.AnyValue();
                jsonValue.setStringValue(attr.getValue().getStringValue());
                jsonKv.setValue(jsonValue);
                jsonResource.getAttributes().add(jsonKv);
            }
            jsonResourceSpans.setResource(jsonResource);

            // Convert ScopeSpans
            for (ScopeSpans ss : rs.getScopeSpansList()) {
                OtlpTraceResponse.ScopeSpans jsonScopeSpans = new OtlpTraceResponse.ScopeSpans();

                // Convert Scope
                OtlpTraceResponse.Scope jsonScope = new OtlpTraceResponse.Scope();
                jsonScope.setName(ss.getScope().getName());
                jsonScope.setVersion(ss.getScope().getVersion());
                jsonScopeSpans.setScope(jsonScope);

                // Convert Spans
                for (Span span : ss.getSpansList()) {
                    OtlpTraceResponse.Span jsonSpan = new OtlpTraceResponse.Span();
                    jsonSpan.setTraceId(bytesToHex(span.getTraceId().toByteArray()));
                    // The SkyWalking SpanId is not hex
                    jsonSpan.setSpanId(TraceType.ZIPKIN.equals(traceType) ? bytesToHex(span.getSpanId().toByteArray()) : span.getSpanId().toStringUtf8());
                    if (!span.getParentSpanId().isEmpty()) {
                        jsonSpan.setParentSpanId(TraceType.ZIPKIN.equals(traceType) ? bytesToHex(span.getParentSpanId().toByteArray()) : span.getParentSpanId().toStringUtf8());
                    }
                    jsonSpan.setName(span.getName());
                    jsonSpan.setKind(span.getKind().name());
                    jsonSpan.setStartTimeUnixNano(String.valueOf(span.getStartTimeUnixNano()));
                    jsonSpan.setEndTimeUnixNano(String.valueOf(span.getEndTimeUnixNano()));

                    // Convert Status
                    if (span.hasStatus()) {
                        OtlpTraceResponse.Status jsonStatus = new OtlpTraceResponse.Status();
                        jsonStatus.setCode(span.getStatus().getCode().name());
                        if (!span.getStatus().getMessage().isEmpty()) {
                            jsonStatus.setMessage(span.getStatus().getMessage());
                        }
                        jsonSpan.setStatus(jsonStatus);
                    }

                    // Convert Attributes
                    for (KeyValue attr : span.getAttributesList()) {
                        OtlpTraceResponse.KeyValue jsonKv = new OtlpTraceResponse.KeyValue();
                        jsonKv.setKey(attr.getKey());
                        OtlpTraceResponse.AnyValue jsonValue = new OtlpTraceResponse.AnyValue();
                        jsonValue.setStringValue(attr.getValue().getStringValue());
                        jsonKv.setValue(jsonValue);
                        jsonSpan.getAttributes().add(jsonKv);
                    }

                    // Convert Events
                    for (Span.Event event : span.getEventsList()) {
                        OtlpTraceResponse.Event jsonEvent = new OtlpTraceResponse.Event();
                        jsonEvent.setTimeUnixNano(String.valueOf(event.getTimeUnixNano()));
                        jsonEvent.setName(event.getName());

                        // Convert Event Attributes
                        for (KeyValue attr : event.getAttributesList()) {
                            OtlpTraceResponse.KeyValue jsonKv = new OtlpTraceResponse.KeyValue();
                            jsonKv.setKey(attr.getKey());
                            OtlpTraceResponse.AnyValue jsonValue = new OtlpTraceResponse.AnyValue();
                            jsonValue.setStringValue(attr.getValue().getStringValue());
                            jsonKv.setValue(jsonValue);
                            jsonEvent.getAttributes().add(jsonKv);
                        }

                        jsonSpan.getEvents().add(jsonEvent);
                    }

                    jsonScopeSpans.getSpans().add(jsonSpan);
                }

                jsonResourceSpans.getScopeSpans().add(jsonScopeSpans);
            }

            traceData.getResourceSpans().add(jsonResourceSpans);
        }

        response.setTrace(traceData);
        return response;
    }

    /**
     * Convert hex string to byte array.
     */
    public static byte[] hexToBytes(String hex) throws DecoderException {
        return Hex.decodeHex(hex);
    }


    /**
     * Convert byte array to hex string.
     */
    public static String bytesToHex(byte[] bytes) {
        return Hex.encodeHexString(bytes);
    }

    public enum TraceType {
        ZIPKIN,
        SKYWALKING
    }
}
