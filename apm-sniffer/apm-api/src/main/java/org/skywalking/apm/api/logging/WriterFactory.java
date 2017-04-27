package org.skywalking.apm.api.logging;

import org.skywalking.apm.api.conf.Config;
import org.skywalking.apm.api.util.StringUtil;

public class WriterFactory {
    public static IWriter getLogWriter() {
        if (!StringUtil.isEmpty(Config.Logging.DIR)) {
            return FileWriter.get();
        } else {
            return SystemOutWriter.INSTANCE;
        }
    }
}
