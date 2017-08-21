package org.skywalking.apm.collector.agentstream.mock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.skywalking.apm.collector.agentstream.HttpClientTools;
import org.skywalking.apm.collector.agentstream.worker.register.application.dao.ApplicationEsDAO;
import org.skywalking.apm.collector.agentstream.worker.register.instance.dao.InstanceEsDAO;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.core.CollectorException;
import org.skywalking.apm.collector.storage.define.register.ApplicationDataDefine;
import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;

/**
 * @author pengys5
 */
public class SegmentPost {

    public static void main(String[] args) throws IOException, InterruptedException, CollectorException {
        ElasticSearchClient client = new ElasticSearchClient("CollectorDBCluster", true, "127.0.0.1:9300");
        client.initialize();

        InstanceEsDAO instanceEsDAO = new InstanceEsDAO();
        instanceEsDAO.setClient(client);

        InstanceDataDefine.Instance consumerInstance = new InstanceDataDefine.Instance("2", 2, "dubbox-consumer", 1501858094526L, 2, 1501858094526L, "");
        instanceEsDAO.save(consumerInstance);
        InstanceDataDefine.Instance providerInstance = new InstanceDataDefine.Instance("3", 3, "dubbox-provider", 1501858094526L, 3, 1501858094526L, "");
        instanceEsDAO.save(providerInstance);

        ApplicationEsDAO applicationEsDAO = new ApplicationEsDAO();
        applicationEsDAO.setClient(client);

        ApplicationDataDefine.Application consumerApplication = new ApplicationDataDefine.Application("2", "dubbox-consumer", 2);
        applicationEsDAO.save(consumerApplication);
        ApplicationDataDefine.Application providerApplication = new ApplicationDataDefine.Application("3", "dubbox-provider", 3);
        applicationEsDAO.save(providerApplication);

        while (true) {
            JsonElement consumer = JsonFileReader.INSTANCE.read("json/segment/normal/dubbox-consumer.json");
            modifyTime(consumer);
            HttpClientTools.INSTANCE.post("http://localhost:12800/segments", consumer.toString());

            JsonElement provider = JsonFileReader.INSTANCE.read("json/segment/normal/dubbox-provider.json");
            modifyTime(provider);
            HttpClientTools.INSTANCE.post("http://localhost:12800/segments", provider.toString());

            Thread.sleep(200);
        }
    }

    private static void modifyTime(JsonElement jsonElement) {
        JsonArray segmentArray = jsonElement.getAsJsonArray();
        for (JsonElement element : segmentArray) {
            JsonObject segmentObj = element.getAsJsonObject();
            JsonArray spans = segmentObj.get("sg").getAsJsonObject().get("ss").getAsJsonArray();
            for (JsonElement span : spans) {
                long startTime = span.getAsJsonObject().get("st").getAsLong();
                long endTime = span.getAsJsonObject().get("et").getAsLong();
                long currentTime = System.currentTimeMillis();
                span.getAsJsonObject().addProperty("st", currentTime);
                span.getAsJsonObject().addProperty("et", currentTime + (endTime - startTime));
            }
        }
    }
}
