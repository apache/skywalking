package org.skywalking.jedis.v2.plugin;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

import org.junit.Test;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;

public class JedisTest {

	@Test
	public void test() throws IllegalAccessException,
			IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, ClassNotFoundException {
		TracingBootstrap
				.main(new String[] { "org.skywalking.jedis.v2.plugin.JedisTest" });
	}

	public static void main(String[] args) throws ClassNotFoundException,
			SQLException, InterruptedException {}

}
