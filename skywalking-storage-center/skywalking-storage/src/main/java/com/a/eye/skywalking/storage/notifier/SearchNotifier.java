package com.a.eye.skywalking.storage.notifier;

import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.network.listener.TraceSearchNotifier;
import com.a.eye.skywalking.storage.data.SpanDataFinder;
import com.a.eye.skywalking.storage.data.spandata.SpanData;

import java.util.List;

public class SearchNotifier implements TraceSearchNotifier {

    @Override
    public List<Span> search(String s) {
        List<SpanData> data = SpanDataFinder.find(s);
        SpanDataHelper helper = new SpanDataHelper(data);
        return helper.category().mergeData();
    }
}
