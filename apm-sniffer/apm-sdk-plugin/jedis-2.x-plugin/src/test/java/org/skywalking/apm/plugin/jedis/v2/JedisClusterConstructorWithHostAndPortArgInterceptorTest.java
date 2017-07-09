package org.skywalking.apm.plugin.jedis.v2;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import redis.clients.jedis.HostAndPort;

import static org.mockito.Mockito.*;
import static org.skywalking.apm.plugin.jedis.v2.JedisMethodInterceptor.*;

@RunWith(MockitoJUnitRunner.class)
public class JedisClusterConstructorWithHostAndPortArgInterceptorTest {

    private JedisClusterConstructorWithHostAndPortArgInterceptor interceptor;

    @Mock
    private EnhancedInstance enhancedInstance;

    @Before
    public void setUp() throws Exception {
        interceptor = new JedisClusterConstructorWithHostAndPortArgInterceptor();
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void onConstruct() throws Exception {
        interceptor.onConstruct(enhancedInstance, new Object[] {new HostAndPort("127.0.0.1", 6379)});
        verify(enhancedInstance, times(1)).setSkyWalkingDynamicField("127.0.0.1:6379");
    }

}
