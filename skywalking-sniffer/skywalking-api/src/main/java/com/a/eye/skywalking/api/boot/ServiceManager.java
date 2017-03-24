package com.a.eye.skywalking.api.boot;

import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogManager;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * The <code>ServiceManager</code> bases on {@link ServiceLoader},
 * load all {@link BootService} implementations.
 *
 * @author wusheng
 */
public enum ServiceManager {
    INSTANCE;

    private static ILog logger = LogManager.getLogger(StatusBootService.class);
    private Map<Class, BootService> bootedServices = new HashMap<Class, BootService>();

    public void boot() {
        bootedServices = loadAllServices();
    }

    private Map<Class, BootService> loadAllServices() {
        HashMap<Class, BootService> bootedServices = new HashMap<Class, BootService>();
        Iterator<BootService> serviceIterator = load().iterator();
        while (serviceIterator.hasNext()) {
            BootService bootService = serviceIterator.next();
            try {
                bootService.bootUp();
                bootedServices.put(bootService.getClass(), bootService);
            } catch (Throwable e) {
                logger.error(e, "ServiceManager try to start [{}] fail.", bootService.getClass().getName());
            }
        }
        return bootedServices;
    }

    /**
     * Find a {@link BootService} implementation, which is already started.
     *
     * @param serviceClass class name.
     * @param <T> {@link BootService} implementation class.
     * @return {@link BootService} instance
     */
    public <T extends BootService> T findService(Class<T> serviceClass) {
        return (T)bootedServices.get(serviceClass);
    }

    ServiceLoader<BootService> load() {
        return ServiceLoader.load(BootService.class);
    }
}
