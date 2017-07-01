package org.skywalking.apm.agent.core.boot;

import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

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

    private static final ILog logger = LogManager.getLogger(ServiceManager.class);
    private Map<Class, BootService> bootedServices = new HashMap<Class, BootService>();

    public void boot() {
        bootedServices = loadAllServices();

        beforeBoot();
        startup();
        afterBoot();
    }

    private Map<Class, BootService> loadAllServices() {
        HashMap<Class, BootService> bootedServices = new HashMap<Class, BootService>();
        Iterator<BootService> serviceIterator = load().iterator();
        while (serviceIterator.hasNext()) {
            BootService bootService = serviceIterator.next();
            bootedServices.put(bootService.getClass(), bootService);
        }
        return bootedServices;
    }

    private void beforeBoot() {
        for (BootService service : bootedServices.values()) {
            try {
                service.beforeBoot();
            } catch (Throwable e) {
                logger.error(e, "ServiceManager try to pre-start [{}] fail.", service.getClass().getName());
            }
        }
    }

    private void startup() {
        for (BootService service : bootedServices.values()) {
            try {
                service.boot();
            } catch (Throwable e) {
                logger.error(e, "ServiceManager try to start [{}] fail.", service.getClass().getName());
            }
        }
    }

    private void afterBoot(){
        for (BootService service : bootedServices.values()) {
            try {
                service.afterBoot();
            } catch (Throwable e) {
                logger.error(e, "Service [{}] AfterBoot process fails.", service.getClass().getName());
            }
        }
    }

    /**
     * Find a {@link BootService} implementation, which is already started.
     *
     * @param serviceClass class name.
     * @param <T>          {@link BootService} implementation class.
     * @return {@link BootService} instance
     */
    public <T extends BootService> T findService(Class<T> serviceClass) {
        return (T) bootedServices.get(serviceClass);
    }

    ServiceLoader<BootService> load() {
        return ServiceLoader.load(BootService.class);
    }
}
