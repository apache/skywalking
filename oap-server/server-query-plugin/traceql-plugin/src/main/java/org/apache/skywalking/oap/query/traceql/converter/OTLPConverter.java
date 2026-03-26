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
                    jsonSpan.setSpanId(TraceType.Zipkin.equals(traceType) ? bytesToHex(span.getSpanId().toByteArray()) : span.getSpanId().toStringUtf8());
                    if (!span.getParentSpanId().isEmpty()) {
                        jsonSpan.setParentSpanId(TraceType.Zipkin.equals(traceType)? bytesToHex(span.getParentSpanId().toByteArray()) : span.getParentSpanId().toStringUtf8());
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
        Zipkin,
        SkyWalking
    }
}
