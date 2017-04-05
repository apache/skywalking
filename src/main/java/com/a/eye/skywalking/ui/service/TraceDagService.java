package com.a.eye.skywalking.ui.service;

import com.a.eye.skywalking.ui.creator.ImageCache;
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
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
@Service
public class TraceDagService {

    private Logger logger = LogManager.getFormatterLogger(TraceDagService.class);

    private Gson gson = new Gson();

    @Autowired
    private ImageCache imageCache;

    @Autowired
    private UrlCreator urlCreator;

    public JsonObject buildGraphData(String timeSliceType, long startTime, long endTime) throws IOException {
        return loadDataFromServer(timeSliceType, startTime, endTime);
    }

    public JsonObject loadDataFromServer(String timeSliceType, long startTime, long endTime) throws IOException {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("timeSliceType", timeSliceType));
        params.add(new BasicNameValuePair("startTime", String.valueOf(startTime)));
        params.add(new BasicNameValuePair("endTime", String.valueOf(endTime)));

        String traceDagUrl = urlCreator.compound("/traceDag/timeSlice");
        String traceDagResponse = HttpClientTools.INSTANCE.get(traceDagUrl, params);
        System.out.println(traceDagResponse);

        JsonObject dagJsonObj = gson.fromJson(traceDagResponse, JsonObject.class).get("result").getAsJsonObject();
        JsonArray nodesArray = dagJsonObj.get("nodes").getAsJsonArray();

        JsonArray newNodesArray = new JsonArray();
        for (int i = 0; i < nodesArray.size(); i++) {
            JsonObject nodeJsonObj = nodesArray.get(i).getAsJsonObject();
            Integer id = nodeJsonObj.get("id").getAsInt();
            String peer = nodeJsonObj.get("peer").getAsString();
            if (nodeJsonObj.has("component") && !StringUtils.isEmpty(nodeJsonObj.get("component"))) {
                String component = nodeJsonObj.get("component").getAsString();
                nodeJsonObj = createNodeGraph(id, peer, imageCache.getImage(component));
            } else {
                nodeJsonObj = createNodeGraph(id, peer, ImageCache.UNDEFINED_IMAGE);
            }

            newNodesArray.add(nodeJsonObj);
        }
        dagJsonObj.add("nodes", newNodesArray);

        return dagJsonObj;
    }

    private JsonObject createNodeGraph(int id, String label, String image) {
        JsonObject nodeJsonObj = new JsonObject();
        nodeJsonObj.addProperty("id", id);
        nodeJsonObj.addProperty("label", label);
        nodeJsonObj.addProperty("image", image);
        return nodeJsonObj;
    }
}
