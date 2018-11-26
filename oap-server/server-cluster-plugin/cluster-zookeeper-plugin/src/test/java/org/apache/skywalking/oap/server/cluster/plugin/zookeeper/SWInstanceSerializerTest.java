package org.apache.skywalking.oap.server.cluster.plugin.zookeeper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;

/**
 * Created by qibaichao on 2018/11/21.
 */
public class SWInstanceSerializerTest {

    public static void main(String args[]){
        Gson gson = new Gson();
        gson.fromJson("{\"name\":\"remote\",\"id\":\"8cb64e6c-029c-441b-807e-d67be33e6bd9\",\"address\":\"0.0.0.0\",\"port\":11800,\"payload\":{\"host\":\"0.0.0.0\",\"port\":11800,\"isSelf\":true},\"registrationTimeUTC\":1542772311558,\"serviceType\":\"DYNAMIC\",\"enabled\":true}", new TypeToken<ServiceInstance<RemoteInstance>>() {
        }.getType());
    }
}
