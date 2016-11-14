package com.a.eye.skywalking.storage.listener;

import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.network.listener.TraceSearchListener;
import com.a.eye.skywalking.storage.data.SpanDataFinder;
import com.a.eye.skywalking.storage.data.spandata.SpanData;
import com.a.eye.skywalking.storage.data.spandata.SpanDataHelper;

import java.util.List;

public class SearchListener implements TraceSearchListener {

    @Override
    public List<Span> search(String s) {
        List<SpanData> data = SpanDataFinder.find(s);
        SpanDataHelper helper = new SpanDataHelper(data);
        return helper.category().mergeData();
    }
}
