package org.apache.skywalking.apm.plugin.canal;

import com.alibaba.otter.canal.client.impl.ClusterNodeAccessStrategy;
import com.alibaba.otter.canal.common.zookeeper.ZkClientx;
import com.alibaba.otter.canal.common.zookeeper.ZookeeperPathUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;


/**
 * @author withlin
 */
public class ClusterNodeConstructInterceptor implements InstanceConstructorInterceptor {
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {

        String clusterPath = ZookeeperPathUtils.getDestinationClusterRoot(allArguments[0].toString());
        ZkClientx zkClientx =  ((ClusterNodeAccessStrategy) objInst).getZkClient();
        ContextManager.getRuntimeContext().put("currentAddress",getCurrentAddress(zkClientx.getChildren(clusterPath)));

    }

    private List<InetSocketAddress> getCurrentAddress(List<String> currentChilds) {
        List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>();
        for (String address : currentChilds) {
            String[] strs = StringUtils.split(address, ":");
            if (strs != null && strs.length == 2) {
                addresses.add(new InetSocketAddress(strs[0], Integer.valueOf(strs[1])));
            }
        }

        return  addresses;

    }
}


