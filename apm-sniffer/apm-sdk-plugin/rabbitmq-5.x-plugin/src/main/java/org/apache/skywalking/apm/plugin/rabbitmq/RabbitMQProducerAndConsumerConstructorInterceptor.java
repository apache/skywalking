package org.apache.skywalking.apm.plugin.rabbitmq;

import com.rabbitmq.client.Connection;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

public class RabbitMQProducerAndConsumerConstructorInterceptor implements InstanceConstructorInterceptor {
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        Connection connection = (Connection)allArguments[0];
        String url =  connection.getAddress().toString().replace("/","") + ":" + connection.getPort();
        objInst.setSkyWalkingDynamicField(url);
    }
}
