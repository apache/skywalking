package org.skywalking.apm.agent.core.collector.sender;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.skywalking.apm.agent.core.collector.entity.InstanceInfo;
import org.skywalking.apm.agent.core.conf.Config;

public class InstanceRegistrySender extends HttpPostSender<InstanceInfo> {

    private Listener listener;

    @Override
    public String url() {
        return Config.Collector.Services.INSTANCE_REGISTRY;
    }

    @Override
    public String serializeData(InstanceInfo data) {
        return new Gson().toJson(data);
    }

    @Override
    protected void dealWithResponse(int statusCode, String responseBody) {
        if (statusCode != 200) {
            return;
        }

        String instanceId = fetchInstanceId(responseBody);
        if (instanceId != null && instanceId.length() > 0) {
            if (listener != null) {
                listener.success(Integer.parseInt(instanceId));
            }
        }
    }

    private String fetchInstanceId(String responseBody) {
        JsonObject jsonObject = new Gson().fromJson(responseBody, JsonObject.class);
        return jsonObject.get("ii").getAsString();
    }

    public InstanceRegistrySender addListener(Listener listener) {
        this.listener = listener;
        return this;
    }

    public interface Listener {
        void success(int instanceId);
    }
}
