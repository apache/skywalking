package com.a.eye.skywalking.collector.worker.nodeinst.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.nodeinst.NodeInstIndex;
import com.a.eye.skywalking.collector.worker.tools.UrlTools;
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
abstract class AbstractNodeInstAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(AbstractNodeInstAnalysis.class);

    AbstractNodeInstAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    void analyseSpans(TraceSegment segment, long timeSlice) throws Exception {
        List<Span> spanList = segment.getSpans();
        if (spanList != null && spanList.size() > 0) {
            for (Span span : spanList) {
                if (span.getParentSpanId() == -1) {
                    String code = segment.getApplicationCode();
                    String kind = Tags.SPAN_KIND.get(span);
                    String component = Tags.COMPONENT.get(span);
                    String url = Tags.URL.get(span);
                    url = UrlTools.parse(url, component);

                    JsonObject dataJsonObj = new JsonObject();
                    dataJsonObj.addProperty(NodeInstIndex.Code, code);
                    dataJsonObj.addProperty(NodeInstIndex.Kind, kind);
                    dataJsonObj.addProperty(NodeInstIndex.Component, component);
                    dataJsonObj.addProperty(NodeInstIndex.Address, url);
                    dataJsonObj.addProperty(NodeInstIndex.Time_Slice, timeSlice);

                    String id = timeSlice + "-" + url;
                    setRecord(id, dataJsonObj);
                    logger.debug("node instance: %s", dataJsonObj.toString());

                    break;
                }
            }
        }
    }
}
