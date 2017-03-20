package com.a.eye.skywalking.collector.worker.node.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.storage.index.AbstractIndex;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.tag.Tags;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * @author pengys5
 */
public abstract class AbstractNodeAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(AbstractNodeAnalysis.class);

    public AbstractNodeAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    public void analyseSpans(TraceSegment segment, long timeSlice) throws Exception {
        List<Span> spanList = segment.getSpans();
        if (spanList != null && spanList.size() > 0) {
            for (Span span : spanList) {
                JsonObject dataJsonObj = new JsonObject();
                String kind = Tags.SPAN_KIND.get(span);

                String layer = null;
                String component = null;
                String code = null;
                if (Tags.SPAN_KIND_CLIENT.equals(kind)) {
                    layer = Tags.SPAN_LAYER.get(span);
                    component = Tags.COMPONENT.get(span);
                    code = Tags.PEERS.get(span) + "-" + component;
                } else if (Tags.SPAN_KIND_SERVER.equals(kind)) {
                    code = segment.getApplicationCode();
                    layer = Tags.SPAN_LAYER.get(span);
                    component = Tags.COMPONENT.get(span);
                } else {
                    logger.error("The span kind value is incorrect which segment record id is %s, the value must client or server", segment.getTraceSegmentId());
                    return;
                }
                dataJsonObj.addProperty("code", code);
                dataJsonObj.addProperty("component", component);
                dataJsonObj.addProperty("layer", layer);
                dataJsonObj.addProperty("kind", kind);
                dataJsonObj.addProperty(AbstractIndex.Time_Slice_Column_Name, timeSlice);
                String id = timeSlice + "-" + code;
                logger.debug("node: %s", dataJsonObj.toString());
                setRecord(id, dataJsonObj);
            }
        }
    }
}