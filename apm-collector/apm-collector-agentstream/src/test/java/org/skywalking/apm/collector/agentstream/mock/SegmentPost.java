/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentstream.mock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.skywalking.apm.collector.agentregister.worker.application.dao.IApplicationDAO;
import org.skywalking.apm.collector.agentregister.worker.instance.dao.IInstanceDAO;
import org.skywalking.apm.collector.agentregister.worker.servicename.dao.IServiceNameDAO;
import org.skywalking.apm.collector.agentstream.HttpClientTools;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.core.CollectorException;
import org.skywalking.apm.collector.core.config.SystemConfig;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.define.register.ApplicationDataDefine;
import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;
import org.skywalking.apm.collector.storage.define.register.ServiceNameDataDefine;
import org.skywalking.apm.collector.storage.elasticsearch.StorageElasticSearchModuleDefine;
import org.skywalking.apm.collector.storage.h2.StorageH2ModuleDefine;

/**
 * @author peng-yongsheng
 */
public class SegmentPost {

    public static void main(String[] args) throws IOException, InterruptedException, CollectorException {
        SystemConfig.DATA_PATH = "/Users/pengys5/code/sky-walking/sky-walking/apm-collector/data";

        ElasticSearchClient elasticSearchClient = new ElasticSearchClient("CollectorDBCluster", true, "127.0.0.1:9300");
        elasticSearchClient.initialize();
        StorageElasticSearchModuleDefine storageElasticSearchModuleDefine = new StorageElasticSearchModuleDefine();
        storageElasticSearchModuleDefine.injectClientIntoDAO(elasticSearchClient);

        H2Client h2Client = new H2Client("jdbc:h2:tcp://localhost/~/test", "sa", "");
        h2Client.initialize();
        StorageH2ModuleDefine storageH2ModuleDefine = new StorageH2ModuleDefine();
        storageH2ModuleDefine.injectClientIntoDAO(h2Client);

        long now = TimeBucketUtils.INSTANCE.getSecondTimeBucket(System.currentTimeMillis());

        IInstanceDAO instanceDAO = (IInstanceDAO)DAOContainer.INSTANCE.get(IInstanceDAO.class.getName());
        InstanceDataDefine.Instance consumerInstance = new InstanceDataDefine.Instance("2", 2, "dubbox-consumer", now, 2, now, osInfo("consumer").toString());
        instanceDAO.save(consumerInstance);
        InstanceDataDefine.Instance providerInstance = new InstanceDataDefine.Instance("3", 3, "dubbox-provider", now, 3, now, osInfo("provider").toString());
        instanceDAO.save(providerInstance);

        IApplicationDAO applicationDAO = (IApplicationDAO)DAOContainer.INSTANCE.get(IApplicationDAO.class.getName());

        ApplicationDataDefine.Application userApplication = new ApplicationDataDefine.Application("1", "User", 1);
        applicationDAO.save(userApplication);
        ApplicationDataDefine.Application consumerApplication = new ApplicationDataDefine.Application("2", "dubbox-consumer", 2);
        applicationDAO.save(consumerApplication);
        ApplicationDataDefine.Application providerApplication = new ApplicationDataDefine.Application("3", "dubbox-provider", 3);
        applicationDAO.save(providerApplication);
        ApplicationDataDefine.Application peer = new ApplicationDataDefine.Application("4", "172.25.0.4:20880", 4);
        applicationDAO.save(peer);

        IServiceNameDAO serviceNameDAO = (IServiceNameDAO)DAOContainer.INSTANCE.get(IServiceNameDAO.class.getName());

        ServiceNameDataDefine.ServiceName serviceName1 = new ServiceNameDataDefine.ServiceName("1", "", 0, 1);
        serviceNameDAO.save(serviceName1);
        ServiceNameDataDefine.ServiceName serviceName2 = new ServiceNameDataDefine.ServiceName("2", "org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()", 2, 2);
        serviceNameDAO.save(serviceName2);
        ServiceNameDataDefine.ServiceName serviceName3 = new ServiceNameDataDefine.ServiceName("3", "/dubbox-case/case/dubbox-rest", 2, 3);
        serviceNameDAO.save(serviceName3);
        ServiceNameDataDefine.ServiceName serviceName4 = new ServiceNameDataDefine.ServiceName("4", "org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()", 3, 4);
        serviceNameDAO.save(serviceName4);

        while (true) {
            JsonElement consumer = JsonFileReader.INSTANCE.read("json/segment/normal/dubbox-consumer.json");
            modifyTime(consumer);
            HttpClientTools.INSTANCE.post("http://localhost:12800/segments", consumer.toString());

            JsonElement provider = JsonFileReader.INSTANCE.read("json/segment/normal/dubbox-provider.json");
            modifyTime(provider);
            HttpClientTools.INSTANCE.post("http://localhost:12800/segments", provider.toString());

            DIFF = 0;
            Thread.sleep(1000);
            break;
        }
    }

    private static long DIFF = 0;

    private static void modifyTime(JsonElement jsonElement) {
        JsonArray segmentArray = jsonElement.getAsJsonArray();
        for (JsonElement element : segmentArray) {
            JsonObject segmentObj = element.getAsJsonObject();
            JsonArray spans = segmentObj.get("sg").getAsJsonObject().get("ss").getAsJsonArray();
            for (JsonElement span : spans) {
                long startTime = span.getAsJsonObject().get("st").getAsLong();
                long endTime = span.getAsJsonObject().get("et").getAsLong();

                if (DIFF == 0) {
                    DIFF = System.currentTimeMillis() - startTime;
                }

                span.getAsJsonObject().addProperty("st", startTime + DIFF);
                span.getAsJsonObject().addProperty("et", endTime + DIFF);
            }
        }
    }

    private static JsonObject osInfo(String hostName) {
        JsonObject osInfoJson = new JsonObject();
        osInfoJson.addProperty("osName", "Linux");
        osInfoJson.addProperty("hostName", hostName);
        osInfoJson.addProperty("processId", 1);

        JsonArray ipv4Array = new JsonArray();
        ipv4Array.add("123.123.123.123");
        ipv4Array.add("124.124.124.124");
        osInfoJson.add("ipv4s", ipv4Array);

        return osInfoJson;
    }
}
