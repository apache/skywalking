package com.ai.cloud.skywalking.plugin.jdbc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.plugin.boot.BootException;
import com.ai.cloud.skywalking.plugin.boot.BootPluginDefine;

public class JDBCPluginDefine extends BootPluginDefine {
    private static Logger logger = LogManager.getLogger(JDBCPluginDefine.class);

    @Override
    protected byte[] boot() throws BootException {
        try {
            Class<?> classes = Class.forName("java.sql.DriverInfo");
            Object traceDriverInfo = newDriverInfoInstance(classes);
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

        return null;
	}

    private Object newDriverInfoInstance(Class<?> classes) throws NoSuchMethodException, InstantiationException, IllegalAccessException, java.lang.reflect.InvocationTargetException {
        //DriverInfo 只有一个构造函数
        Constructor constructor = classes.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        // JDK 1.7 DriverInfo 只有一个Driver入参构造函数
        if (constructor.getParameterTypes().length == 1){
            return constructor.newInstance(new TracingDriver());
        }
        // JDK1.8 DriverInfo 有两个入参的构造函数(Driver, DriverAction)
        return constructor.newInstance(new TracingDriver(), null);
    }
}
