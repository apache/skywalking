package org.apache.skywalking.oap.server.library.module;

import java.lang.reflect.Field;

public interface ModuleConfigInitiator {

    Object init(ModuleDefine currentModule, Field field, String propertyKey, Object propertyValue);
}
