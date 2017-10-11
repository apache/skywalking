package org.skywalking.apm.collector.storage.h2;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author pengys5
 */
public class StorageH2ConfigParser implements ModuleConfigParser {
    private static final String URL = "url";
    public static final String USER_NAME = "user_name";
    public static final String PASSWORD = "password";
    @Override public void parse(Map config) throws ConfigParseException {
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(URL))) {
            StorageH2Config.URL = (String)config.get(URL);
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(USER_NAME))) {
            StorageH2Config.USER_NAME = (String)config.get(USER_NAME);
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(PASSWORD))) {
            StorageH2Config.PASSWORD = (String)config.get(PASSWORD);
        }
    }
}
