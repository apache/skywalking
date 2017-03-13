package com.a.eye.skywalking.api.boot;

import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogManager;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * The <code>ServiceStarter</code> bases on {@link ServiceLoader},
 * load all {@link BootService} implementations.
 *
 * @author wusheng
 */
public enum ServiceStarter {
    INSTANCE;

    private static ILog logger = LogManager.getLogger(StatusBootService.class);
    private volatile boolean isStarted = false;
    private Map<Class, BootService> bootedServices;

    public void boot() {
        if (!isStarted) {
            try {
                bootedServices = new HashMap<>();
                Iterator<BootService> serviceIterator = load().iterator();
                while (serviceIterator.hasNext()) {
                    BootService bootService = serviceIterator.next();
                    try {
                        bootService.bootUp();
                        bootedServices.put(bootService.getClass(), bootService);
                    } catch (Exception e) {
                        logger.error(e, "ServiceStarter try to start [{}] fail.", bootService.getClass().getName());
                    }
                }
            } finally {
                isStarted = true;
            }
        }
    }

    /**
     * Find a {@link BootService} implementation, which is already started.
     * @param serviceClass class name.
     * @param <T> {@link BootService} implementation class.
     * @return {@link BootService} instance
     */
    public <T extends BootService> T findService(Class<T> serviceClass){
        return (T)bootedServices.get(serviceClass);
    }

    ServiceLoader<BootService> load() {
        return ServiceLoader.load(BootService.class);
    }
}
