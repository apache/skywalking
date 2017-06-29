package org.skywalking.apm.agent.core.discovery;

import org.skywalking.apm.agent.core.boot.StatusBootService;
import org.skywalking.apm.agent.core.remote.DiscoveryRestServiceClient;

/**
 * The <code>CollectorDiscoveryService</code> is responsible for start {@link DiscoveryRestServiceClient}.
 *
 * @author wusheng
 */
public class CollectorDiscoveryService extends StatusBootService {
    @Override
    protected void bootUpWithStatus() throws Exception {
        Thread collectorClientThread = new Thread(new DiscoveryRestServiceClient(), "collectorClientThread");
        collectorClientThread.start();
    }
}
