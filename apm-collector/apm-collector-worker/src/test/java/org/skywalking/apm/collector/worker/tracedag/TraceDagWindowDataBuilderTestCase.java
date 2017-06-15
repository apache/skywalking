package org.skywalking.apm.collector.worker.tracedag;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

/**
 * @author pengys5
 */
public class TraceDagWindowDataBuilderTestCase {

    private Gson gson = new Gson();

    @Test
    public void testBuild() {
        JsonArray nodeMappingArray = nodeMappingArrayData();
        JsonArray nodeRefArray = nodeRefArrayData();
        JsonArray nodeCompArray = nodeCompArrayData();
        JsonArray resSumArray = resSumArrayData();
        JsonArray nodeCountArrayData = nodeCountArrayData();

        TraceDagDataBuilder builder = new TraceDagDataBuilder();
        JsonObject dagJsonObj = builder.build(nodeCompArray, nodeMappingArray, nodeRefArray, resSumArray, nodeCountArrayData);

        JsonArray pointArray = dagJsonObj.getAsJsonArray("nodes");
        JsonArray lineArray = dagJsonObj.getAsJsonArray("nodeRefs");

        for (int i = 0; i < pointArray.size(); i++) {
//            System.out.println(pointArray.get(i).getAsJsonObject());
        }

        for (int i = 0; i < lineArray.size(); i++) {
//            System.out.println(lineArray.get(i).getAsJsonObject());
        }
    }

    private JsonArray nodeMappingArrayData() {
        String str = "[{\"code\":\"cache-service\",\"peers\":\"[127.0.0.1:8002]\"},{\"code\":\"persistence-service\",\"peers\":\"[10.128.35.80:20880]\"}]";
        JsonArray jsonArray = gson.fromJson(str, JsonArray.class);
        return jsonArray;
    }

    private JsonArray nodeRefArrayData() {
        String str = "[{\"front\":\"User\",\"behind\":\"portal-service\"},{\"front\":\"portal-service\",\"behind\":\"[127.0.0.1:8002]\"},{\"front\":\"cache-service\",\"behind\":\"[127.0.0.1:6379]\"},{\"front\":\"cache-service\",\"behind\":\"[localhost:-1]\"},{\"front\":\"persistence-service\",\"behind\":\"[127.0.0.1:3307]\"},{\"front\":\"portal-service\",\"behind\":\"[10.128.35.80:20880]\"}]";
        JsonArray jsonArray = gson.fromJson(str, JsonArray.class);
        return jsonArray;
    }

    private JsonArray nodeCompArrayData() {
        String str = "[{\"name\":\"Tomcat\",\"peers\":\"portal-service\"},{\"name\":\"Motan\",\"peers\":\"cache-service\"},{\"name\":\"H2\",\"peers\":\"[localhost:-1]\"},{\"name\":\"Tomcat\",\"peers\":\"[10.128.35.80:57818]\"},{\"name\":\"Redis\",\"peers\":\"[127.0.0.1:6379]\"},{\"name\":\"Mysql\",\"peers\":\"[127.0.0.1:3307]\"},{\"name\":\"Tomcat\",\"peers\":\"persistence-service\"},{\"name\":\"HttpClient\",\"peers\":\"[10.128.35.80:20880]\"},{\"name\":\"Motan\",\"peers\":\"[127.0.0.1:8002]\"},{\"name\":\"Tomcat\",\"peers\":\"[0:0:0:0:0:0:0:1:57837]\"}]";
        JsonArray jsonArray = gson.fromJson(str, JsonArray.class);
        return jsonArray;
    }

    private JsonArray resSumArrayData() {
        String str = "[{\"front\":\"User\",\"behind\":\"portal-service\",\"oneSecondLess\":1.0,\"threeSecondLess\":0.0,\"fiveSecondLess\":0.0,\"fiveSecondGreater\":0.0,\"error\":0.0,\"summary\":1.0},{\"front\":\"cache-service\",\"behind\":\"[127.0.0.1:6379]\",\"oneSecondLess\":10.0,\"threeSecondLess\":0.0,\"fiveSecondLess\":0.0,\"fiveSecondGreater\":0.0,\"error\":0.0,\"summary\":10.0},{\"front\":\"cache-service\",\"behind\":\"[localhost:-1]\",\"oneSecondLess\":4.0,\"threeSecondLess\":0.0,\"fiveSecondLess\":0.0,\"fiveSecondGreater\":0.0,\"error\":0.0,\"summary\":4.0},{\"front\":\"persistence-service\",\"behind\":\"[127.0.0.1:3307]\",\"oneSecondLess\":2.0,\"threeSecondLess\":0.0,\"fiveSecondLess\":0.0,\"fiveSecondGreater\":0.0,\"error\":0.0,\"summary\":2.0},{\"front\":\"portal-service\",\"behind\":\"[10.128.35.80:20880]\",\"oneSecondLess\":1.0,\"threeSecondLess\":0.0,\"fiveSecondLess\":0.0,\"fiveSecondGreater\":0.0,\"error\":0.0,\"summary\":1.0},{\"front\":\"portal-service\",\"behind\":\"[127.0.0.1:8002]\",\"oneSecondLess\":2.0,\"threeSecondLess\":0.0,\"fiveSecondLess\":0.0,\"fiveSecondGreater\":0.0,\"error\":0.0,\"summary\":2.0}]";
        JsonArray jsonArray = gson.fromJson(str, JsonArray.class);
        return jsonArray;
    }

    private JsonArray nodeCountArrayData() {
        String str = "[{\"ac\":\"cache-service\",\"count\":1},{\"ac\":\"persistence-service\",\"count\":2}, {\"ac\":\"portal-service\",\"count\":2}]";
        JsonArray jsonArray = gson.fromJson(str, JsonArray.class);
        return jsonArray;
    }
}
