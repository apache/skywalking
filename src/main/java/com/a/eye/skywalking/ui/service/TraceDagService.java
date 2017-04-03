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
//        String nodeInstSumResponse = HttpClientTools.INSTANCE.get(nodeInstSumUrl, params);
//        String nodeInstSumResponse = "{\"result\":[{\"timeSlice\":\"201704020000\",\"oneSecondLess\":20.0,\"threeSecondLess\":1.0,\"fiveSecondLess\":0.0,\"fiveSecondGreater\":0.0,\"error\":0.0,\"summary\":21.0}]}";


        String nodeRefUrl = urlCreator.compound("/nodeRef/timeSlice");
        String nodeRefResponse = HttpClientTools.INSTANCE.get(nodeRefUrl, params);
//        String nodeRefResponse = "{\"result\":[{\"front\":\"User\",\"frontIsRealCode\":true,\"behind\":\"portal-service\",\"behindIsRealCode\":true,\"timeSlice\":201704020000},{\"front\":\"cache-service\",\"frontIsRealCode\":true,\"behind\":\"[127.0.0.1:6379]\",\"behindIsRealCode\":false,\"timeSlice\":201704020000},{\"front\":\"cache-service\",\"frontIsRealCode\":true,\"behind\":\"[localhost:-1]\",\"behindIsRealCode\":false,\"timeSlice\":201704020000},{\"front\":\"persistence-service\",\"frontIsRealCode\":true,\"behind\":\"[127.0.0.1:3307]\",\"behindIsRealCode\":false,\"timeSlice\":201704020000},{\"front\":\"portal-service\",\"frontIsRealCode\":true,\"behind\":\"[127.0.0.1:8002]\",\"behindIsRealCode\":false,\"timeSlice\":201704020000},{\"front\":\"portal-service\",\"frontIsRealCode\":true,\"behind\":\"[192.168.1.11:20880]\",\"behindIsRealCode\":false,\"timeSlice\":201704020000}]}";

        String nodeRefResSumUrl = urlCreator.compound("/nodeRef/resSum/timeSlice");
        String nodeRefResSumResponse = HttpClientTools.INSTANCE.get(nodeRefResSumUrl, params);
//        String nodeRefResSumResponse = "{\"result\":[{\"front\":\"User\",\"behind\":\"portal-service\",\"oneSecondLess\":1.0,\"threeSecondLess\":1.0,\"fiveSecondLess\":0.0,\"fiveSecondGreater\":0.0,\"error\":0.0,\"summary\":2.0},{\"front\":\"cache-service\",\"behind\":\"[127.0.0.1:6379]\",\"oneSecondLess\":5.0,\"threeSecondLess\":0.0,\"fiveSecondLess\":0.0,\"fiveSecondGreater\":0.0,\"error\":0.0,\"summary\":5.0},{\"front\":\"cache-service\",\"behind\":\"[localhost:-1]\",\"oneSecondLess\":4.0,\"threeSecondLess\":0.0,\"fiveSecondLess\":0.0,\"fiveSecondGreater\":0.0,\"error\":0.0,\"summary\":4.0},{\"front\":\"persistence-service\",\"behind\":\"[127.0.0.1:3307]\",\"oneSecondLess\":4.0,\"threeSecondLess\":0.0,\"fiveSecondLess\":0.0,\"fiveSecondGreater\":0.0,\"error\":0.0,\"summary\":4.0},{\"front\":\"portal-service\",\"behind\":\"[127.0.0.1:8002]\",\"oneSecondLess\":4.0,\"threeSecondLess\":0.0,\"fiveSecondLess\":0.0,\"fiveSecondGreater\":0.0,\"error\":0.0,\"summary\":4.0},{\"front\":\"portal-service\",\"behind\":\"[192.168.1.11:20880]\",\"oneSecondLess\":2.0,\"threeSecondLess\":0.0,\"fiveSecondLess\":0.0,\"fiveSecondGreater\":0.0,\"error\":0.0,\"summary\":2.0}]}";

        String nodeUrl = urlCreator.compound("/node/timeSlice");
        String nodeResponse = HttpClientTools.INSTANCE.get(nodeUrl, params);
//        String nodeResponse = "{\"result\":[{\"code\":\"User\",\"component\":\"User\",\"nickName\":\"User\",\"timeSlice\":201704020000},{\"code\":\"[127.0.0.1:3307]\",\"component\":\"Mysql\",\"nickName\":\"[127.0.0.1:3307]\",\"timeSlice\":201704020000},{\"code\":\"[127.0.0.1:6379]\",\"component\":\"Redis\",\"nickName\":\"[127.0.0.1:6379]\",\"timeSlice\":201704020000},{\"code\":\"[127.0.0.1:8002]\",\"component\":\"Motan\",\"nickName\":\"[127.0.0.1:8002]\",\"timeSlice\":201704020000},{\"code\":\"[192.168.1.11:20880]\",\"component\":\"HttpClient\",\"nickName\":\"[192.168.1.11:20880]\",\"timeSlice\":201704020000},{\"code\":\"[localhost:-1]\",\"component\":\"H2\",\"nickName\":\"[localhost:-1]\",\"timeSlice\":201704020000},{\"code\":\"cache-service\",\"component\":\"Motan\",\"nickName\":\"[127.0.0.1:8002]\",\"timeSlice\":201704020000},{\"code\":\"persistence-service\",\"component\":\"Tomcat\",\"nickName\":\"[192.168.1.11:20880]\",\"timeSlice\":201704020000},{\"code\":\"portal-service\",\"component\":\"Tomcat\",\"nickName\":\"portal-service\",\"timeSlice\":201704020000}]}";

        String nodeInstUrl = urlCreator.compound("/nodeInst/timeSlice");
//        String nodeInstResponse = HttpClientTools.INSTANCE.get(nodeInstUrl, params);
        String nodeInstResponse = "";

        JsonObject nodes = gson.fromJson(nodeResponse, JsonObject.class);
        logger.debug("nodes: %s", nodes.toString());
        JsonArray nodesArray = nodes.get("result").getAsJsonArray();

        JsonObject nodeRef = gson.fromJson(nodeRefResponse, JsonObject.class);
        logger.debug("nodeRef: %s", nodeRef.toString());
        JsonArray nodeRefArray = nodeRef.get("result").getAsJsonArray();

        JsonObject nodeRefResSum = gson.fromJson(nodeRefResSumResponse, JsonObject.class);
        logger.debug("nodeRefResSum: %s", nodeRefResSum.toString());
        JsonArray nodeRefResSumArray = nodeRefResSum.get("result").getAsJsonArray();

//        JsonObject nodeInstSum = gson.fromJson(nodeInstSumResponse, JsonObject.class);
//        JsonArray nodeInstSumArray = nodeInstSum.get("result").getAsJsonArray();

//        JsonObject nodeInst = gson.fromJson(nodeInstResponse, JsonObject.class);
//        JsonArray nodeInstArray = nodeInst.get("result").getAsJsonArray();

//        Map<String, Integer> sumMapping = buildNodeInstSumMapping(nodeInstSumArray);
        nodeRefArray = nodeRefCodeRename(nodeRefArray, nodesArray);

        Map<String, JsonObject> nodeRefResSumMapping = nodeRefResSumCodeRename(nodeRefResSumArray, nodesArray);

        return graphBuildService.buildNodesGraph(nodesArray, nodeRefArray, nodeRefResSumMapping);
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
        String realName = refNodeNickName;

        for (int i = 0; i < nodesArray.size(); i++) {
            JsonObject node = nodesArray.get(i).getAsJsonObject();
            String code = node.get("code").getAsString();
            String nickName = node.get("nickName").getAsString();

            if (nickName.equals(refNodeNickName)) {
                if (code.startsWith("[") && code.endsWith("]")) {
                    realName = code;
                } else {
                    return code;
                }
            }
        }
        return realName;
    }

}
