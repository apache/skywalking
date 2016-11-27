package com.a.eye.skywalking.storage.listener;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.network.grpc.TraceId;
import com.a.eye.skywalking.network.listener.server.AsyncTraceSearchServerListener;
import com.a.eye.skywalking.storage.data.SpanDataFinder;
import com.a.eye.skywalking.storage.data.spandata.SpanData;
import com.a.eye.skywalking.storage.data.spandata.SpanDataHelper;

import java.util.ArrayList;
import java.util.List;

public class SearchListener implements AsyncTraceSearchServerListener {

    private static ILog logger = LogManager.getLogger(SearchListener.class);

    @Override
    public List<Span> search(TraceId traceId) {
        try {
            List<SpanData> data = SpanDataFinder.find(traceId);
            SpanDataHelper helper = new SpanDataHelper(data);
            List<Span> span = helper.category().mergeData();
            HealthCollector.getCurrentHeathReading("SearchListener")
                    .updateData(HeathReading.INFO, span.size() + "  spans was found by trace Id [" + traceId + "].");
            return span;
        } catch (Exception e) {
            logger.error("Search trace Id[{}] failure.", traceId, e);
            HealthCollector.getCurrentHeathReading("SearchListener")
                    .updateData(HeathReading.ERROR, "Search trace Id[" + traceId + "] failure.");
            return new ArrayList<Span>();
        }
    }
}
