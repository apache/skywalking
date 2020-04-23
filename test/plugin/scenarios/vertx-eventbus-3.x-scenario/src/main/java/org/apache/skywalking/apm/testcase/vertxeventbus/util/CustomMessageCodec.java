package org.apache.skywalking.apm.testcase.vertxeventbus.util;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

public class CustomMessageCodec implements MessageCodec<CustomMessage, CustomMessage> {

    @Override
    public void encodeToWire(Buffer buffer, CustomMessage customMessage) {
        JsonObject jsonToEncode = new JsonObject();
        jsonToEncode.put("statusCode", customMessage.getStatusCode());
        jsonToEncode.put("resultCode", customMessage.getResultCode());
        jsonToEncode.put("summary", customMessage.getSummary());

        String jsonToStr = jsonToEncode.encode();
        int length = jsonToStr.getBytes().length;
        buffer.appendInt(length);
        buffer.appendString(jsonToStr);
    }

    @Override
    public CustomMessage decodeFromWire(int position, Buffer buffer) {
        int _pos = position;
        int length = buffer.getInt(_pos);
        String jsonStr = buffer.getString(_pos += 4, _pos += length);
        JsonObject contentJson = new JsonObject(jsonStr);

        int statusCode = contentJson.getInteger("statusCode");
        String resultCode = contentJson.getString("resultCode");
        String summary = contentJson.getString("summary");
        return new CustomMessage(statusCode, resultCode, summary);
    }

    @Override
    public CustomMessage transform(CustomMessage customMessage) {
        return customMessage;
    }

    @Override
    public String name() {
        return this.getClass().getSimpleName();
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
