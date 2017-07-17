package org.skywalking.apm.agent.core.remote;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.conf.Config;

/**
 * The <code>CollectorDiscoveryService</code> is responsible for start {@link DiscoveryRestServiceClient}.
 *
 * @author wusheng
 */
public class CollectorDiscoveryService implements BootService {
    @Override
    public void beforeBoot() throws Throwable {

    }

    @Override
    public void boot() throws Throwable {
        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(new DiscoveryRestServiceClient(), 0,
                Config.Collector.DISCOVERY_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    public void afterBoot() throws Throwable {

    }
}
