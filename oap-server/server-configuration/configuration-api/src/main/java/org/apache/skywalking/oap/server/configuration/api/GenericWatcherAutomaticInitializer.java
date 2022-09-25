package org.apache.skywalking.oap.server.configuration.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.apache.skywalking.oap.server.library.module.ModuleConfigInitiator;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;

public interface GenericWatcherAutomaticInitializer extends ModuleConfigInitiator {

    @Override
    default Object init(ModuleDefine currentModule, Field field, String propertyKey, Object propertyValue) {
        if (!(propertyValue instanceof String)) {
            return null;
        }
        final WatcherAutomationInject annotation = field.getAnnotation(WatcherAutomationInject.class);

        if (annotation == null) {
            return null;
        }
        String itermName = annotation.watcherKey().isEmpty() ? propertyKey : annotation.watcherKey();
        String value = (String) propertyValue;
        Class<?> fieldClass = field.getType();
        try {
            if (ConfigChangeWatcher.class.isAssignableFrom(fieldClass)) {
                final Constructor<?> constructor = fieldClass.getConstructor(
                    String.class, String.class, ModuleProvider.class);
                return constructor.newInstance(itermName, value, currentModule.provider());
            }
        } catch (Exception e) {
            //ignore
        }
        return null;
    }

    default void registerWatcherTo(DynamicConfigurationService service) {
        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.getAnnotation(WatcherAutomationInject.class) != null) {
                field.setAccessible(true);
                try {
                    service.registerConfigChangeWatcher((ConfigChangeWatcher) field.get(this));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface WatcherAutomationInject {
        String watcherKey() default "";
    }

}
