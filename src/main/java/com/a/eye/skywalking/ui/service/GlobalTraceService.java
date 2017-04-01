package com.a.eye.skywalking.ui.service;

import com.a.eye.skywalking.ui.creator.UrlCreator;
import com.a.eye.skywalking.ui.tools.HttpClientTools;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
@Service
public class GlobalTraceService {

    private Logger logger = LogManager.getFormatterLogger(GlobalTraceService.class);

    private Gson gson = new Gson();

    @Autowired
    private UrlCreator urlCreator;

    public String loadGlobalTraceData(String globalId) throws IOException {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("globalId", globalId));

        String globalTraceLoadUrl = urlCreator.compound("/globalTrace/globalId");
        String globalTraceResponse = HttpClientTools.INSTANCE.get(globalTraceLoadUrl, params);

//        String globalTraceResponse = "{\"result\":\"[{\\\"spanId\\\":0,\\\"segId\\\":\\\"SEGMENT.1\\\",\\\"spanSegId\\\":\\\"SEGMENT.1--0\\\",\\\"parentSpanSegId\\\":\\\"-1\\\",\\\"startTime\\\":1490666636631,\\\"relativeStartTime\\\":0,\\\"cost\\\":32,\\\"operationName\\\":\\\"SEGMENT.1.SPAN.0.OPERATIONNAME\\\",\\\"childSpans\\\":[{\\\"spanId\\\":1,\\\"segId\\\":\\\"SEGMENT.1\\\",\\\"spanSegId\\\":\\\"SEGMENT.1--1\\\",\\\"parentSpanSegId\\\":\\\"SEGMENT.1--0\\\",\\\"startTime\\\":1490666636632,\\\"relativeStartTime\\\":1,\\\"cost\\\":30,\\\"operationName\\\":\\\"SEGMENT.1.SPAN.1.OPERATIONNAME\\\",\\\"childSpans\\\":[{\\\"spanId\\\":0,\\\"segId\\\":\\\"SEGMENT.2\\\",\\\"spanSegId\\\":\\\"SEGMENT.2--0\\\",\\\"parentSpanSegId\\\":\\\"SEGMENT.1--1\\\",\\\"startTime\\\":1490666636632,\\\"relativeStartTime\\\":1,\\\"cost\\\":10,\\\"operationName\\\":\\\"SEGMENT.2.SPAN.0.OPERATIONNAME\\\",\\\"childSpans\\\":[{\\\"spanId\\\":1,\\\"segId\\\":\\\"SEGMENT.2\\\",\\\"spanSegId\\\":\\\"SEGMENT.2--1\\\",\\\"parentSpanSegId\\\":\\\"SEGMENT.2--0\\\",\\\"startTime\\\":1490666636642,\\\"relativeStartTime\\\":11,\\\"cost\\\":10,\\\"operationName\\\":\\\"SEGMENT.2.SPAN.1.OPERATIONNAME\\\",\\\"childSpans\\\":[]}]}]}]}]\"}";
        JsonObject globalObj = gson.fromJson(globalTraceResponse, JsonObject.class);

        logger.debug("load loadGlobalTraceData data: %s", globalTraceResponse);

        return globalObj.get("result").toString();
    }
}
