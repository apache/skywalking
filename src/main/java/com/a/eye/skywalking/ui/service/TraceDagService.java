package com.a.eye.skywalking.ui.service;

import com.a.eye.skywalking.ui.creator.UrlCreator;
import com.a.eye.skywalking.ui.tools.HttpClientTools;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author pengys5
 */
@Service
public class TraceDagService {

    private Logger logger = LogManager.getFormatterLogger(TraceDagService.class);

    private Gson gson = new GsonBuilder().serializeNulls().create();

    @Autowired
    private UrlCreator urlCreator;

    @Autowired
    private TraceDagGraphBuildService graphBuildService;

    public JsonObject buildGraphData(String timeSliceType, long startTime, long endTime) throws IOException {
        return loadDataFromServer(timeSliceType, startTime, endTime);
    }

    public JsonObject loadDataFromServer(String timeSliceType, long startTime, long endTime) throws IOException {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("timeSliceType", timeSliceType));
        params.add(new BasicNameValuePair("startTime", String.valueOf(startTime)));
        params.add(new BasicNameValuePair("endTime", String.valueOf(endTime)));

        String nodeInstSumUrl = urlCreator.compound("/nodeInst/summary/timeSlice");
        String nodeInstSumResponse = HttpClientTools.INSTANCE.get(nodeInstSumUrl, params);

        String nodeRefUrl = urlCreator.compound("/nodeRef/timeSlice");
        String nodeRefResponse = HttpClientTools.INSTANCE.get(nodeRefUrl, params);

        String nodeRefResSumUrl = urlCreator.compound("/nodeRef/resSum/timeSlice");
        String nodeRefResSumResponse = HttpClientTools.INSTANCE.get(nodeRefResSumUrl, params);

        String nodeUrl = urlCreator.compound("/node/timeSlice");
        String nodeResponse = HttpClientTools.INSTANCE.get(nodeUrl, params);

        String nodeInstUrl = urlCreator.compound("/nodeInst/timeSlice");
        String nodeInstResponse = HttpClientTools.INSTANCE.get(nodeInstUrl, params);

        JsonObject nodes = gson.fromJson(nodeResponse, JsonObject.class);
        logger.debug("nodes: %s", nodes.toString());
        JsonArray nodesArray = nodes.get("result").getAsJsonArray();

        JsonObject nodeRef = gson.fromJson(nodeRefResponse, JsonObject.class);
        logger.debug("nodeRef: %s", nodeRef.toString());
        JsonArray nodeRefArray = nodeRef.get("result").getAsJsonArray();

        JsonObject nodeRefResSum = gson.fromJson(nodeRefResSumResponse, JsonObject.class);
        logger.debug("nodeRefResSum: %s", nodeRefResSum.toString());
        JsonArray nodeRefResSumArray = nodeRefResSum.get("result").getAsJsonArray();

        JsonObject nodeInstSum = gson.fromJson(nodeInstSumResponse, JsonObject.class);
        JsonArray nodeInstSumArray = nodeInstSum.get("result").getAsJsonArray();

        JsonObject nodeInst = gson.fromJson(nodeInstResponse, JsonObject.class);
        JsonArray nodeInstArray = nodeInst.get("result").getAsJsonArray();

        Map<String, Integer> sumMapping = buildNodeInstSumMapping(nodeInstSumArray);
        nodeRefArray = nodeRefCodeRename(nodeRefArray, nodesArray);

        Map<String, JsonObject> nodeRefResSumMapping = nodeRefResSumCodeRename(nodeRefResSumArray, nodesArray);

        return graphBuildService.buildNodesGraph(nodesArray, nodeRefArray, nodeInst, sumMapping, nodeRefResSumMapping);
    }

    private Map<String, Integer> buildNodeInstSumMapping(JsonArray nodeInstSumArray) {
        Map<String, Integer> sumMapping = new HashMap<>();
        for (int i = 0; i < nodeInstSumArray.size(); i++) {
            JsonObject nodeInstSum = nodeInstSumArray.get(i).getAsJsonObject();
            String code = nodeInstSum.get("code").getAsString();
            int count = nodeInstSum.get("count").getAsInt();
            sumMapping.put(code, count);
        }
        return sumMapping;
    }

    private JsonArray nodeRefCodeRename(JsonArray nodeRefArray, JsonArray nodesArray) {
        JsonArray newNodeRefArray = new JsonArray();
        for (int i = 0; i < nodeRefArray.size(); i++) {
            JsonObject nodeRef = nodeRefArray.get(i).getAsJsonObject();
            String front = nodeRef.get("front").getAsString();
            boolean frontIsRealCode = nodeRef.get("frontIsRealCode").getAsBoolean();
            if (!frontIsRealCode) {
                front = findCodeByNickName(front, nodesArray);
            }

            String behind = nodeRef.get("behind").getAsString();
            boolean behindIsRealCode = nodeRef.get("behindIsRealCode").getAsBoolean();
            if (!behindIsRealCode) {
                behind = findCodeByNickName(behind, nodesArray);
            }
            nodeRef.addProperty("front", front);
            nodeRef.addProperty("behind", behind);
            newNodeRefArray.add(nodeRef);
        }
        return newNodeRefArray;
    }

    private Map<String, JsonObject> nodeRefResSumCodeRename(JsonArray nodeRefResSumArray, JsonArray nodesArray) {
        Map<String, JsonObject> nodeRefResSumMapping = new HashMap<>();

        for (int i = 0; i < nodeRefResSumArray.size(); i++) {
            JsonObject nodeRefResSum = nodeRefResSumArray.get(i).getAsJsonObject();
            String front = nodeRefResSum.get("front").getAsString();
            if (front.contains("[") && front.contains("]")) {
                front = findCodeByNickName(front, nodesArray);
            }

            String behind = nodeRefResSum.get("behind").getAsString();
            if (behind.contains("[") && behind.contains("]")) {
                behind = findCodeByNickName(behind, nodesArray);
            }
            String nodeRefId = front + "-" + behind;
            nodeRefResSumMapping.put(nodeRefId, nodeRefResSum);
        }
        return nodeRefResSumMapping;
    }

    private String findCodeByNickName(String refNodeNickName, JsonArray nodesArray) {
        for (int i = 0; i < nodesArray.size(); i++) {
            JsonObject node = nodesArray.get(i).getAsJsonObject();
            String code = node.get("code").getAsString();
            String nickName = node.get("nickName").getAsString();

            if (nickName.equals(refNodeNickName)) {
                return code;
            }
        }
        return refNodeNickName;
    }

}
