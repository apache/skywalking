package org.skywalking.apm.collector.commons.serializer;

import akka.serialization.JSerializer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public class JsonSerializer extends JSerializer {
    @Override
    public boolean includeManifest() {
        return false;
    }

    @Override
    public int identifier() {
        return 31;
    }

    @Override
    public byte[] toBinary(Object o) {
        JsonObject jsonObject = (JsonObject) o;
        return jsonObject.toString().getBytes();
    }

    @Override
    public Object fromBinaryJava(byte[] bytes, Class<?> manifest) {
        Gson gson = new Gson();
        return gson.fromJson(new String(bytes), JsonObject.class);
    }
}
