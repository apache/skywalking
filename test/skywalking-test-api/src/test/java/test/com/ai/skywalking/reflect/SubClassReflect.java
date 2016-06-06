package test.com.ai.skywalking.reflect;

import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by xin on 16-6-6.
 */
public class SubClassReflect {

    @Test
    public void fetchSubClassField() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class testSubClass = loader.loadClass("test.com.ai.skywalking.reflect.TestClass$TestSubClass");
        Field field = testSubClass.getDeclaredField("testStringArray");
        assertNotNull(field);
        field.setAccessible(true);
        Object[] objects = (Object[]) field.get(testSubClass);
        assertEquals(5, objects.length);
    }
}
