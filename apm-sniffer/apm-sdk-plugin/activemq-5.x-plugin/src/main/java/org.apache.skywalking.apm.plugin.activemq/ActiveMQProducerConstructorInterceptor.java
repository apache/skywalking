package org.apache.skywalking.apm.plugin.activemq;

import org.apache.activemq.ActiveMQSession;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

public class ActiveMQProducerConstructorInterceptor implements InstanceConstructorInterceptor {
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        ActiveMQSession session = (ActiveMQSession)allArguments[0];
        objInst.setSkyWalkingDynamicField(session.getConnection().getTransport().getRemoteAddress().split("//")[1]);
    }
}
