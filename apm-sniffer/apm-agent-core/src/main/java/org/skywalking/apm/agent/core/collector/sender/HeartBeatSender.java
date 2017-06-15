package org.skywalking.apm.agent.core.collector.sender;

import com.google.gson.Gson;
import org.skywalking.apm.agent.core.collector.entity.Ping;
import org.skywalking.apm.agent.core.conf.Config;

public class HeartBeatSender extends HttpPostSender<Ping> {
    @Override
    public String url() {
        return Config.Collector.Services.HEART_BEAT_REPORT;
    }

    @Override
    public String serializeData(Ping data) {
        return new Gson().toJson(data);
    }

    @Override
    protected void dealWithResponse(int statusCode, String responseBody) {

    }
}
