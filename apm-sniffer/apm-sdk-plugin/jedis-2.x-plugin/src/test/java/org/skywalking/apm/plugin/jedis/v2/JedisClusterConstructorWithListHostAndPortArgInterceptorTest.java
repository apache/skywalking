package org.skywalking.apm.plugin.jedis.v2;

import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import redis.clients.jedis.HostAndPort;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class JedisClusterConstructorWithListHostAndPortArgInterceptorTest {

    private JedisClusterConstructorWithListHostAndPortArgInterceptor interceptor;

    private Set<HostAndPort> hostAndPortSet;

    @Mock
    private EnhancedInstance enhancedInstance;

    @Before
    public void setUp() throws Exception {
        hostAndPortSet = new HashSet<HostAndPort>();
        interceptor = new JedisClusterConstructorWithListHostAndPortArgInterceptor();
        hostAndPortSet.add(new HostAndPort("127.0.0.1", 6379));
        hostAndPortSet.add(new HostAndPort("127.0.0.1", 16379));
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void onConstruct() throws Exception {
        interceptor.onConstruct(enhancedInstance, new Object[] {hostAndPortSet});

        verify(enhancedInstance, times(1)).setSkyWalkingDynamicField("127.0.0.1:6379;127.0.0.1:16379;");
    }

}
