package com.ai.cloud.skywalking.alarm.util;

import com.ai.cloud.skywalking.alarm.conf.Config;
import com.ai.cloud.skywalking.alarm.dao.SystemConfigDao;
import freemarker.template.Configuration;
import freemarker.template.TemplateModelException;
import freemarker.template.Version;

import java.sql.SQLException;

public class TemplateConfigurationUtil {

    private static Configuration cfg;

    public static Configuration getConfiguration() throws SQLException, TemplateModelException {
        if (cfg == null) {
            cfg = new Configuration(new Version("2.3.23"));
            cfg.setDefaultEncoding("UTF-8");
            cfg.setSharedVariable("portalAddr", SystemConfigDao.getSystemConfig(Config.TemplateInfo.CONFIG_ID));
        }

        return cfg;
    }
}
