package com.a.eye.skywalking.collector.worker.application.metric;


import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractMember;
import com.a.eye.skywalking.collector.actor.AbstractMemberProvider;
import com.a.eye.skywalking.collector.actor.MemberSystem;
import com.a.eye.skywalking.collector.actor.selector.LocalSelector;
import com.a.eye.skywalking.collector.worker.application.persistence.TraceSegmentRecordPersistence;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.TraceSegmentRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/**
 * @author pengys5
 */
public class TraceSegmentRecordMember extends AbstractMember {

    public TraceSegmentRecordMember(MemberSystem memberSystem, ActorRef actorRef) throws Throwable {
        super(memberSystem, actorRef);
    }

    @Override
    public void preStart() throws Exception {
    }

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof TraceSegment) {
            TraceSegment traceSegment = (TraceSegment) message;
            JsonObject traceSegmentJsonObj = parseTraceSegment(traceSegment);

            tell(new TraceSegmentRecordPersistence.Factory(), LocalSelector.INSTANCE, traceSegmentJsonObj);
        }
    }

    public static class Factory extends AbstractMemberProvider {
        @Override
        public Class memberClass() {
            return TraceSegmentRecordMember.class;
        }
    }

    private JsonObject parseTraceSegment(TraceSegment traceSegment) {
        JsonObject traceJsonObj = new JsonObject();
        traceJsonObj.addProperty("segmentId", traceSegment.getTraceSegmentId());
        traceJsonObj.addProperty("startTime", traceSegment.getStartTime());
        traceJsonObj.addProperty("endTime", traceSegment.getEndTime());
        traceJsonObj.addProperty("appCode", traceSegment.getApplicationCode());

        if (traceSegment.getPrimaryRef() != null) {
            JsonObject primaryRefJsonObj = parsePrimaryRef(traceSegment.getPrimaryRef());
            traceJsonObj.add("primaryRef", primaryRefJsonObj);
        }

//        if (traceSegment.getRefs() != null) {
//            JsonArray refsJsonArray = parseRefs(traceSegment.getRefs());
//            traceJsonObj.add("refs", refsJsonArray);
//        }

        JsonArray spanJsonArray = new JsonArray();
        for (Span span : traceSegment.getSpans()) {
            JsonObject spanJsonObj = parseSpan(span);
            spanJsonArray.add(spanJsonObj);
        }
        traceJsonObj.add("spans", spanJsonArray);

        return traceJsonObj;
    }

    private JsonObject parsePrimaryRef(TraceSegmentRef primaryRef) {
        JsonObject primaryRefJsonObj = new JsonObject();
        primaryRefJsonObj.addProperty("appCode", primaryRef.getApplicationCode());
        primaryRefJsonObj.addProperty("spanId", primaryRef.getSpanId());
        primaryRefJsonObj.addProperty("peerHost", primaryRef.getPeerHost());
        primaryRefJsonObj.addProperty("segmentId", primaryRef.getTraceSegmentId());
        return primaryRefJsonObj;
    }

    private JsonArray parseRefs(List<TraceSegmentRef> refs) {
        JsonArray refsJsonArray = new JsonArray();
        for (TraceSegmentRef ref : refs) {
            JsonObject refJsonObj = new JsonObject();
            refJsonObj.addProperty("spanId", ref.getSpanId());
            refJsonObj.addProperty("appCode", ref.getApplicationCode());
            refJsonObj.addProperty("segmentId", ref.getTraceSegmentId());
            refJsonObj.addProperty("peerHost", ref.getPeerHost());
            refsJsonArray.add(refJsonObj);
        }
        return refsJsonArray;
    }

    private JsonObject parseSpan(Span span) {
        JsonObject spanJsonObj = new JsonObject();
        spanJsonObj.addProperty("spanId", span.getSpanId());
        spanJsonObj.addProperty("parentSpanId", span.getParentSpanId());
        spanJsonObj.addProperty("startTime", span.getStartTime());
        spanJsonObj.addProperty("endTime", span.getEndTime());
        spanJsonObj.addProperty("operationName", span.getOperationName());

        JsonObject tagsJsonObj = parseSpanTag(span.getTags());
        spanJsonObj.add("tags", tagsJsonObj);
        return spanJsonObj;
    }

    private JsonObject parseSpanTag(Map<String, Object> tags) {
        JsonObject tagsJsonObj = new JsonObject();

        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            String key = entry.getKey();
            String value = String.valueOf(entry.getValue());
            tagsJsonObj.addProperty(key, value);
        }
        return tagsJsonObj;
    }
}
