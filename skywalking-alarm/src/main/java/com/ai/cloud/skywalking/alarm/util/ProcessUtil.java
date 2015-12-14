package com.ai.cloud.skywalking.alarm.util;

import com.ai.cloud.skywalking.alarm.conf.Config;
import com.ai.cloud.skywalking.alarm.model.ProcessThreadStatus;
import com.ai.cloud.skywalking.alarm.model.ProcessThreadValue;
import com.google.gson.Gson;

public class ProcessUtil {

    public static void changeProcessThreadStatus(String threadId, ProcessThreadStatus status) throws Exception {
        String path = Config.ZKPath.REGISTER_SERVER_PATH + "/" + threadId;
        String value = ZKUtil.getPathData(path);
        ProcessThreadValue newValue = new Gson().fromJson(value, ProcessThreadValue.class);
        newValue.setStatus(status.getValue());
        ZKUtil.setPathData(path, new Gson().toJson(newValue));
    }

}
