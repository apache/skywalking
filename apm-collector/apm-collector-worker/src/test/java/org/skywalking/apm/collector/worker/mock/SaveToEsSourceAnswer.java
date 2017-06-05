package org.skywalking.apm.collector.worker.mock;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * @author pengys5
 */
public class SaveToEsSourceAnswer implements Answer<Object> {

    public SourceObj sourceObj = new SourceObj();

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        Gson gson = new Gson();
        String source = (String) invocation.getArguments()[0];
        JsonObject sourceJsonObj = gson.fromJson(source, JsonObject.class);
        sourceObj.setSource(sourceJsonObj);
        return null;
    }

    public class SourceObj {
        private JsonArray source;

        public SourceObj() {
            source = new JsonArray();
        }

        public void setSource(JsonObject source) {
            this.source.add(source);
        }
    }
}
