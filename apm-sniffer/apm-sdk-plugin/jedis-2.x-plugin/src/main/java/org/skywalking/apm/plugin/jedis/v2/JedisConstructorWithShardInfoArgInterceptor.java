package org.skywalking.apm.plugin.jedis.v2;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import redis.clients.jedis.JedisShardInfo;

public class JedisConstructorWithShardInfoArgInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        String redisConnInfo;
        JedisShardInfo shardInfo = (JedisShardInfo)allArguments[0];
        redisConnInfo = shardInfo.getHost() + ":" + shardInfo.getPort();
        objInst.setSkyWalkingDynamicField(redisConnInfo);
    }
}
