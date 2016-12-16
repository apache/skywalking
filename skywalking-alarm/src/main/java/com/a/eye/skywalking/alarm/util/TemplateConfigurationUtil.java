package com.a.eye.skywalking.alarm.util;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import com.a.eye.skywalking.alarm.conf.Config;

import freemarker.template.Configuration;
import freemarker.template.TemplateModelException;
import freemarker.template.Version;

public class TemplateConfigurationUtil {

    private static Configuration cfg;

    public static Configuration getConfiguration() throws SQLException, TemplateModelException, IOException {
        if (cfg == null) {
            cfg = new Configuration(new Version("2.3.23"));
            cfg.setDefaultEncoding("UTF-8");
            cfg.setSharedVariable("portalAddr", Config.TemplateInfo.PORTAL_URL);
            
            //获取资源路径
            String classPath = cfg.getClass().getResource("/").getFile().toString();
            cfg.setDirectoryForTemplateLoading(new File(classPath));
        }

        return cfg;
    }
}
