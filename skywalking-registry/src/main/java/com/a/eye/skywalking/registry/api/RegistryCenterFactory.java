package com.a.eye.skywalking.registry.api;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Created by xin on 2016/11/10.
 */
public class RegistryCenterFactory {

    private Map<CenterType, RegistryCenter> registryCenter = new HashMap<CenterType, RegistryCenter>();

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

    public RegistryCenter getRegistryCenter(CenterType type) {
        return registryCenter.get(type);
    }
}
