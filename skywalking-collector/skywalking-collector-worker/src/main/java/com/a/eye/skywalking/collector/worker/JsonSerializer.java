package com.a.eye.skywalking.collector.worker;

import akka.serialization.JSerializer;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.proto.SegmentMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.protobuf.InvalidProtocolBufferException;

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
        return 123;
    }

    @Override
    public byte[] toBinary(Object o) {
//        System.out.println("Json toBinary");
        JsonObject jsonObject = (JsonObject) o;
        return jsonObject.toString().getBytes();
    }

    @Override
    public Object fromBinaryJava(byte[] bytes, Class<?> manifest) {
//        System.out.println("Json fromBinaryJava");
        Gson gson = new Gson();
        return gson.fromJson(new String(bytes), JsonObject.class);
    }
}
