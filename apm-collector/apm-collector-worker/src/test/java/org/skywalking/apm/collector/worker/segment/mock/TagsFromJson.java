package org.skywalking.apm.collector.worker.segment.mock;

import com.google.gson.JsonArray;
import java.util.List;
import org.skywalking.apm.network.proto.KeyWithStringValue;

/**
 * @author pengys5
 */
public enum TagsFromJson {
    INSTANCE;

    public List<KeyWithStringValue.Builder> build(JsonArray tagsArray) {
        return KeyWithStringValueFromJson.INSTANCE.build(tagsArray);
    }
}
