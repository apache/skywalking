package com.a.eye.skywalking.api.boot;

import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogManager;
import java.util.Iterator;
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

    public void boot() {
        while (!isStarted) {
            try {
                Iterator<BootService> serviceIterator = load().iterator();
                while (serviceIterator.hasNext()) {
                    BootService bootService = serviceIterator.next();
                    try {
                        bootService.bootUp();
                    } catch (Exception e) {
                        logger.error(e, "ServiceStarter try to start [{}] fail.", bootService.getClass().getName());
                    }
                }
            } finally {
                isStarted = true;
            }
        }
    }

    ServiceLoader<BootService> load() {
        return ServiceLoader.load(BootService.class);
    }
}
