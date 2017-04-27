package org.skywalking.apm.collector.worker.noderef.analysis;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
public class NodeRefResRecordAnswer implements Answer<Object> {

    private Gson gson = new Gson();
    private List<JsonObject> nodeRefResRecordList = new ArrayList<>();

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        AbstractNodeRefResSumAnalysis.NodeRefResRecord nodeRefResRecord = (AbstractNodeRefResSumAnalysis.NodeRefResRecord) invocation.getArguments()[0];
        String recordJsonStr = gson.toJson(nodeRefResRecord);
        JsonObject recordJsonObj = gson.fromJson(recordJsonStr, JsonObject.class);
        nodeRefResRecordList.add(recordJsonObj);
        return null;
    }

    public List<JsonObject> getNodeRefResRecordList() {
        return nodeRefResRecordList;
    }
}
