package com.ai.cloud.skywalking.plugin.jdbc;

import com.ai.cloud.skywalking.plugin.boot.BootException;
import com.ai.cloud.skywalking.plugin.boot.BootPluginDefine;
import com.ai.cloud.skywalking.plugin.jdbc.driver.TracingDriver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.concurrent.CopyOnWriteArrayList;

public class JDBCPluginDefine extends BootPluginDefine {

    private Logger logger = LogManager.getLogger(JDBCPluginDefine.class);

    @Override
    protected void boot() throws BootException {
        try {
            Class classes = Class.forName("java.sql.DriverInfo");
            Constructor constructor = classes.getDeclaredConstructor(Driver.class);
            constructor.setAccessible(true);
            Object traceDriverInfo = constructor.newInstance(new TracingDriver());
            Field field = DriverManager.class.getDeclaredField("registeredDrivers");
            field.setAccessible(true);
            CopyOnWriteArrayList copyOnWriteArrayList = (CopyOnWriteArrayList) field.get(DriverManager.class);
            copyOnWriteArrayList.add(0, traceDriverInfo);
        } catch (Exception e) {
            // 开启补偿机制
            logger.error("Failed to change the byte code of DriverManger, Will open compensation mechanism.", e);
            TracingDriver.registerDriver();
        }
    }
}
