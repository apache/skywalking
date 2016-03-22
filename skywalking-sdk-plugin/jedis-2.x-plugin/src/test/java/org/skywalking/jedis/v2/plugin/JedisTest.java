package org.skywalking.jedis.v2.plugin;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

import org.junit.Test;

import redis.clients.jedis.Jedis;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;

public class JedisTest {
	@Test
	public void test() throws IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException,
			SecurityException, ClassNotFoundException {
		TracingBootstrap
				.main(new String[] { "org.skywalking.jedis.v2.plugin.JedisTest" });
	}

	public static void main(String[] args) throws ClassNotFoundException,
			SQLException, InterruptedException {
		try(Jedis jedis = new Jedis("10.1.241.18", 16379)){
			long start = System.currentTimeMillis();
			jedis.set("11111", "111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111");
			for (int i = 0; i < 1; i++) {
				jedis.get("11111");
			}
			long end = System.currentTimeMillis();
			System.out.println(end - start + "ms");
			jedis.del("11111");
		}
	}
	
	public void testNormal() throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException, InterruptedException{
		JedisTest.main(null);
	}
}
