package org.skywalking.apm.collector.stream.grpc;

import java.util.HashMap;
import java.util.Map;
import org.skywalking.apm.collector.client.grpc.GRPCClient;
import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
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

    @Override public void serverJoinNotify(String serverAddress) {
        String selfAddress = StreamGRPCConfig.HOST + ":" + StreamGRPCConfig.PORT;
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        if (!clients.containsKey(serverAddress)) {
            logger.info("new address: {}, create this address remote worker reference", serverAddress);
            String[] hostPort = serverAddress.split(":");
            GRPCClient client = new GRPCClient(hostPort[0], Integer.valueOf(hostPort[1]));
            try {
                client.initialize();
            } catch (ClientException e) {
                e.printStackTrace();
            }
            clients.put(serverAddress, client);

            if (selfAddress.equals(serverAddress)) {
                context.getClusterWorkerContext().getProviders().forEach(provider -> {
                    logger.info("create remote worker self reference, role: {}", provider.role().roleName());
                    provider.create();
                });
            } else {
                context.getClusterWorkerContext().getProviders().forEach(provider -> {
                    logger.info("create remote worker reference, role: {}", provider.role().roleName());
                    provider.create(client);
                });
            }
        } else {
            logger.info("address: {} had remote worker reference, ignore", serverAddress);
        }
    }

    @Override public void serverQuitNotify() {

    }
}
