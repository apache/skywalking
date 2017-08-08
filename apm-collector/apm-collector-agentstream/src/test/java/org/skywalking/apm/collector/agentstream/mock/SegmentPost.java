package org.skywalking.apm.collector.agentstream.mock;

import com.google.gson.JsonElement;
import java.io.IOException;
import org.skywalking.apm.collector.agentstream.HttpClientTools;
import org.skywalking.apm.collector.agentstream.worker.register.instance.InstanceDataDefine;
import org.skywalking.apm.collector.agentstream.worker.register.instance.dao.InstanceEsDAO;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.core.CollectorException;

/**
 * @author pengys5
 */
public class SegmentPost {

    public static void main(String[] args) throws IOException, InterruptedException, CollectorException {
        ElasticSearchClient client = new ElasticSearchClient("CollectorDBCluster", true, "127.0.0.1:9300");
        client.initialize();

        InstanceEsDAO instanceEsDAO = new InstanceEsDAO();
        instanceEsDAO.setClient(client);

        InstanceDataDefine.Instance consumerInstance = new InstanceDataDefine.Instance("2", 2, "dubbox-consumer", 1501858094526L, 2);
        instanceEsDAO.save(consumerInstance);
        InstanceDataDefine.Instance providerInstance = new InstanceDataDefine.Instance("3", 3, "dubbox-provider", 1501858094526L, 3);
        instanceEsDAO.save(providerInstance);

        JsonElement consumer = JsonFileReader.INSTANCE.read("json/segment/normal/dubbox-consumer.json");
        HttpClientTools.INSTANCE.post("http://localhost:12800/segments", consumer.toString());

        Thread.sleep(5000);

        JsonElement provider = JsonFileReader.INSTANCE.read("json/segment/normal/dubbox-provider.json");
        HttpClientTools.INSTANCE.post("http://localhost:12800/segments", provider.toString());

        Thread.sleep(5000);
    }
}
