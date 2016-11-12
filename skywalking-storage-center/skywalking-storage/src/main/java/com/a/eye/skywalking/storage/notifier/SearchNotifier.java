package com.a.eye.skywalking.storage.notifier;

import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.network.listener.TraceSearchNotifier;
import com.a.eye.skywalking.storage.data.SpanDataFinder;
import com.a.eye.skywalking.storage.data.spandata.AckSpanData;
import com.a.eye.skywalking.storage.data.spandata.RequestSpanData;
import com.a.eye.skywalking.storage.data.spandata.SpanData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchNotifier implements TraceSearchNotifier {

    @Override
    public List<Span> search(String s) {
        List<SpanData> data = SpanDataFinder.find(s);
        return mergeSpanData(data);
    }

    private List<Span> mergeSpanData(List<SpanData> data) {
        //// TODO: 2016/11/12 需要修改
        Map<String, RequestSpanData> requestSpen = new HashMap<String, RequestSpanData>();
        Map<String, AckSpanData> ackSpen = new HashMap<String, AckSpanData>();

        for (SpanData spanData : data) {
            if (spanData instanceof RequestSpanData) {
                requestSpen.put(spanData.getLevelId(), (RequestSpanData) spanData);
            } else {
                ackSpen.put(spanData.getLevelId(), (AckSpanData) spanData);
            }
        }

        List<Span> mergedSpan = new ArrayList<Span>();
        for (Map.Entry<String, RequestSpanData> entry : requestSpen.entrySet()) {
            AckSpanData ackSpanData = ackSpen.get(entry.getKey());
            if (ackSpanData != null) {
                mergedSpan.add(mergeSpan(entry.getValue(), ackSpanData));
            }
        }

        return mergedSpan;
    }

    private Span mergeSpan(RequestSpanData value, AckSpanData ackSpanData) {
        return null;
    }


}
