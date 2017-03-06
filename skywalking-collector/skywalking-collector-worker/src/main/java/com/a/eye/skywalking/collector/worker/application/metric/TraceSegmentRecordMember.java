package com.a.eye.skywalking.collector.worker.application.metric;


import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractASyncMemberProvider;
import com.a.eye.skywalking.collector.actor.AbstractMember;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.PersistenceMember;
import com.a.eye.skywalking.collector.worker.application.persistence.TraceSegmentRecordPersistence;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.TraceSegmentRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lmax.disruptor.EventFactory;

import java.util.List;
import java.util.Map;

/**
 * @author pengys5
 */
public class TraceSegmentRecordMember extends PersistenceMember {

    @Override
    public String esIndex() {
        return "application_record";
    }

    @Override
    public String esType() {
        return "trace_segment";
    }

    public TraceSegmentRecordMember(ActorRef actorRef) throws Throwable {
        super(actorRef);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof TraceSegment) {
            TraceSegment traceSegment = (TraceSegment) message;
            JsonObject traceSegmentJsonObj = parseTraceSegment(traceSegment);

            tell(TraceSegmentRecordPersistence.Factory.INSTANCE, RollingSelector.INSTANCE, traceSegmentJsonObj);
        }
    }

    public static class MessageFactory implements EventFactory<MessageHolder> {
        public static MessageFactory INSTANCE = new MessageFactory();

        public MessageHolder newInstance() {
            return new MessageHolder();
        }
    }

    public static class Factory extends AbstractASyncMemberProvider<TraceSegmentRecordMember> {
        public static Factory INSTANCE = new Factory();

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
