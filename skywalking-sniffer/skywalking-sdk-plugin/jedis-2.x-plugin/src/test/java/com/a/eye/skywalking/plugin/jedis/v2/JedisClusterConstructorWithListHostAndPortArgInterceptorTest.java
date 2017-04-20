package com.a.eye.skywalking.plugin.jedis.v2;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ConstructorInvokeContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.Set;

import redis.clients.jedis.HostAndPort;

import static com.a.eye.skywalking.plugin.jedis.v2.JedisMethodInterceptor.KEY_OF_REDIS_CONN_INFO;
import static com.a.eye.skywalking.plugin.jedis.v2.JedisMethodInterceptor.KEY_OF_REDIS_HOSTS;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JedisClusterConstructorWithListHostAndPortArgInterceptorTest {

    private JedisClusterConstructorWithListHostAndPortArgInterceptor interceptor;
    @Mock
    private EnhancedClassInstanceContext instanceContext;
    @Mock
    private ConstructorInvokeContext invokeContext;

    private Set<HostAndPort> hostAndPortSet;

    @Before
    public void setUp() throws Exception {
        hostAndPortSet = new HashSet<HostAndPort>();
        interceptor = new JedisClusterConstructorWithListHostAndPortArgInterceptor();
        hostAndPortSet.add(new HostAndPort("127.0.0.1", 6379));
        hostAndPortSet.add(new HostAndPort("127.0.0.1", 16379));

        when(invokeContext.allArguments()).thenReturn(new Object[] {hostAndPortSet});
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void onConstruct() throws Exception {
        interceptor.onConstruct(instanceContext, invokeContext);

        verify(instanceContext, times(1)).set(eq(KEY_OF_REDIS_CONN_INFO), contains("127.0.0.1:6379;"));
        verify(instanceContext, times(1)).set(eq(KEY_OF_REDIS_HOSTS), contains("127.0.0.1:16379;"));
    }

}
