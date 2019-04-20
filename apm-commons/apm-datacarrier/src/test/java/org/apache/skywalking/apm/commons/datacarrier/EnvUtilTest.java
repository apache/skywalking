package org.apache.skywalking.apm.commons.datacarrier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author flycash
 * 2019-04-20
 */
public class EnvUtilTest {

    private Map<String, String> writableEnv;
    @Before
    public void before() {
        try {
            Map<String, String> env = System.getenv();
            Class<?> cl = env.getClass();
            Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            writableEnv = (Map<String, String>) field.get(env);
            writableEnv.put("myInt", "123");
            writableEnv.put("wrongInt", "wrong123");
            writableEnv.put("myLong", "12345678901234567");
            writableEnv.put("wrongLong", "wrong123");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set environment variable", e);
        }
    }

    @Test
    public void getInt() {
        assertEquals(123, EnvUtil.getInt("myInt", 234));
        assertEquals(234, EnvUtil.getLong("wrongInt", 234));
    }

    @Test
    public void getLong() {
        assertEquals(12345678901234567L, EnvUtil.getLong("myLong", 123L));
        assertEquals(987654321987654321L, EnvUtil.getLong("wrongLong", 987654321987654321L));
    }

    @After
    public void after() {
        writableEnv.remove("myInt");
        writableEnv.remove("wrongInt");
        writableEnv.remove("myLong");
        writableEnv.remove("wrongLong");
        assertNull(System.getenv("myInt"));
    }


}