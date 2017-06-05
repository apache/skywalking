package org.skywalking.apm.agent.core.logging;

import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.util.StringUtil;

public class WriterFactory {
    public static IWriter getLogWriter() {
        if (!StringUtil.isEmpty(Config.Logging.DIR)) {
            return FileWriter.get();
        } else {
            return SystemOutWriter.INSTANCE;
        }
    }
}
