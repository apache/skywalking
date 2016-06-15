package com.ai.cloud.skywalking.plugin.jdbc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.plugin.boot.BootException;
import com.ai.cloud.skywalking.plugin.boot.BootPluginDefine;
import com.ai.cloud.skywalking.plugin.jdbc.driver.TracingDriver;

public class JDBCPluginDefine extends BootPluginDefine {
    private static Logger logger = LogManager.getLogger(JDBCPluginDefine.class);

    @Override
    protected void boot() throws BootException {
        try {
            Class<?> classes = Class.forName("java.sql.DriverInfo");
            Constructor<?> constructor = classes.getDeclaredConstructor(Driver.class);
            constructor.setAccessible(true);
            Object traceDriverInfo = constructor.newInstance(new TracingDriver());
            Field field = DriverManager.class.getDeclaredField("registeredDrivers");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
			CopyOnWriteArrayList<Object> copyOnWriteArrayList = (CopyOnWriteArrayList<Object>) field.get(DriverManager.class);
            copyOnWriteArrayList.add(0, traceDriverInfo);
        } catch (Throwable e) {
			// 开启补偿机制
			logger.error(
					"Failed to inject TracingDriver to the top of registered Drivers. Need to alter jdbc url to trace.",
					e);
			TracingDriver.registerDriver();
		}
	}
}
