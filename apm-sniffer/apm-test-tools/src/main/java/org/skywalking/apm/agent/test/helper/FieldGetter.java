package org.skywalking.apm.agent.test.helper;

import java.lang.reflect.Field;

public class FieldGetter {
    public static <T> T getValue(Object instance,
        String fieldName) throws IllegalAccessException, NoSuchFieldException {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T)field.get(instance);
    }

    public static <T> T getParentFieldValue(Object instance,
        String fieldName) throws IllegalAccessException, NoSuchFieldException {
        Field field = instance.getClass().getSuperclass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T)field.get(instance);
    }
}
