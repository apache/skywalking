package org.skywalking.apm.collector.stream.grpc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.client.grpc.GRPCClient;
import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.RemoteWorkerRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class StreamGRPCDataListener extends ClusterDataListener {

    private final Logger logger = LoggerFactory.getLogger(StreamGRPCDataListener.class);

    public static final String PATH = ClusterModuleDefine.BASE_CATALOG + "." + StreamModuleGroupDefine.GROUP_NAME + "." + StreamGRPCModuleDefine.MODULE_NAME;

    @Override public String path() {
        return PATH;
    }

    private Map<String, GRPCClient> clients = new HashMap<>();
    private Map<String, RemoteWorkerRef> workerRefs = new HashMap<>();

    @Override public void addressChangedNotify() {
        String selfAddress = StreamGRPCConfig.HOST + ":" + StreamGRPCConfig.PORT;

        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        List<String> addresses = getAddresses();
        clients.keySet().forEach(address -> {
            if (!addresses.contains(address)) {
                context.getClusterWorkerContext().remove(workerRefs.get(address));
                workerRefs.remove(address);
            }
        });

        for (String address : addresses) {
            if (!clients.containsKey(address)) {
                logger.debug("new address: {}, create this address remote worker reference", address);
                String[] hostPort = address.split(":");
                GRPCClient client = new GRPCClient(hostPort[0], Integer.valueOf(hostPort[1]));
                try {
                    client.initialize();
                } catch (ClientException e) {
                    e.printStackTrace();
                }
                clients.put(address, client);

                if (selfAddress.equals(address)) {
                    context.getClusterWorkerContext().getProviders().forEach(provider -> {
                        logger.debug("create remote worker self reference, role: {}", provider.role().roleName());
                        provider.create();
                    });
                } else {
                    context.getClusterWorkerContext().getProviders().forEach(provider -> {
                        logger.debug("create remote worker reference, role: {}", provider.role().roleName());
                        RemoteWorkerRef workerRef = provider.create(client);
                    });
                }
            } else {
                logger.debug("address: {} had remote worker reference, ignore", address);
            }
        }
    }
}
