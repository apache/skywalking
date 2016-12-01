package com.a.eye.skywalking.registry.impl.zookeeper;

import com.a.eye.skywalking.registry.RegistryNodeManager;
import com.a.eye.skywalking.registry.api.Center;
import com.a.eye.skywalking.registry.api.CenterType;
import com.a.eye.skywalking.registry.api.NotifyListener;
import com.a.eye.skywalking.registry.api.RegistryCenter;
import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.ZkClient;

import java.util.List;
import java.util.Properties;

@Center(type = CenterType.DEFAULT_CENTER_TYPE)
public class ZookeeperRegistryCenter implements RegistryCenter {

    private ZkClient client;
    private RegistryNodeManager nodeManager = new RegistryNodeManager();

    @Override
    public void register(String path) {
        String[] pathSegment = path.split("/");
        StringBuilder createPath = new StringBuilder();
        for (int i = 0; i < pathSegment.length - 1; i++) {
            if (pathSegment[i] == null || pathSegment[i].length() == 0)
                continue;

            createPath.append("/" + pathSegment[i]);
            if (!exists(createPath.toString())) {
                client.createPersistent(createPath.toString());
            }
        }

        client.createEphemeral(createPath.append("/" + pathSegment[pathSegment.length - 1]).toString());
    }

    @Override
    public void subscribe(String path, final NotifyListener listener) {
        List<String> children = client.subscribeChildChanges(path, new IZkChildListener() {
            @Override
            public void handleChildChange(String parentPath, List<String> children) throws Exception {
                listener.notify(nodeManager.calculateChangeOfConnectionURL(children));
            }
        });
        if (children != null && children.size() > 0)
            listener.notify(nodeManager.calculateChangeOfConnectionURL(children));
    }

    private boolean exists(String path) {
        return client.exists(path);
    }

    @Override
    public void start(Properties centerConfig) {
        ZookeeperConfig config = new ZookeeperConfig(centerConfig);
        client = new ZkClient(config.getConnectURL(), 60 * 1000);
        if (config.hasAuthInfo()) {
            client.addAuthInfo(config.getAutSchema(), config.getAuth());
        }
    }



}
