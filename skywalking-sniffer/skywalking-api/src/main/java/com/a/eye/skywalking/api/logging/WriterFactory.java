package com.a.eye.skywalking.api.logging;

import com.a.eye.skywalking.api.conf.Config;
import com.a.eye.skywalking.api.util.StringUtil;

public class WriterFactory {
    public static IWriter getLogWriter(){
        if (!StringUtil.isEmpty(Config.Logging.DIR)){
            return FileWriter.get();
        }else{
            return SystemOutWriter.INSTANCE;
        }
    }
}
