package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.storage.index.AbstractIndex;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.TraceSegmentRef;
import com.a.eye.skywalking.trace.tag.Tags;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * @author pengys5
 */
public abstract class AbstractNodeRefAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(AbstractNodeRefAnalysis.class);

    public AbstractNodeRefAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    void analyseRefs(TraceSegment segment, long timeSlice) throws Exception {
        List<TraceSegmentRef> segmentRefList = segment.getRefs();
        if (segmentRefList != null && segmentRefList.size() > 0) {
            for (TraceSegmentRef segmentRef : segmentRefList) {
                String front = segmentRef.getApplicationCode();
                String behind = segment.getApplicationCode();
                String id = timeSlice + "-" + front + "-" + behind;

                JsonObject dataJsonObj = new JsonObject();
                dataJsonObj.addProperty("front", front);
                dataJsonObj.addProperty("behind", behind);
                dataJsonObj.addProperty(AbstractIndex.Time_Slice_Column_Name, timeSlice);
                logger.debug("dag node ref: %s", dataJsonObj.toString());
                setRecord(id, dataJsonObj);
            }
        }
    }

    void analyseSpans(TraceSegment segment, long timeSlice) throws Exception {
        List<Span> spanList = segment.getSpans();
        if (spanList != null && spanList.size() > 0) {
            for (Span span : spanList) {
                if (Tags.SPAN_KIND_CLIENT.equals(Tags.SPAN_KIND.get(span))) {
                    JsonObject dataJsonObj = new JsonObject();
                    String front = segment.getApplicationCode();
                    dataJsonObj.addProperty("front", front);

                    String component = Tags.COMPONENT.get(span);
                    String peers = Tags.PEERS.get(span);
                    String behind = component + "-" + peers;
                    dataJsonObj.addProperty("behind", behind);
                    dataJsonObj.addProperty(AbstractIndex.Time_Slice_Column_Name, timeSlice);

                    String id = timeSlice + "-" + front + "-" + behind;
                    logger.debug("dag node ref: %s", dataJsonObj.toString());
                    setRecord(id, dataJsonObj);
                }
            }
        }
    }
}
