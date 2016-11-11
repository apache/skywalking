package com.a.eye.skywalking.registry.logging;

import com.a.eye.skywalking.registry.logging.api.Center;
import com.a.eye.skywalking.registry.logging.api.RegistryCenter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Created by xin on 2016/11/10.
 */
public class RegistryCenterFactory {

    public static RegistryCenterFactory INSTANCE = new RegistryCenterFactory();

    private Map<String, RegistryCenter> registryCenter = new HashMap<String, RegistryCenter>();

    private RegistryCenterFactory() {
        ServiceLoader<RegistryCenter> loaders = ServiceLoader.load(RegistryCenter.class);
        Iterator<RegistryCenter> iterator = loaders.iterator();
        while (iterator.hasNext()) {
            RegistryCenter center = iterator.next();
            Center centerInfo = center.getClass().getAnnotation(Center.class);
            if (centerInfo == null) {
                continue;
            }

            registryCenter.put(centerInfo.type(), center);

        }
    }

    public RegistryCenter getRegistryCenter(String type) {
        return registryCenter.get(type);
    }


}
