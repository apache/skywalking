package org.skywalking.apm.plugin.jedis.v2;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ConstructorInvokeContext;

import static org.mockito.Mockito.*;
import static org.skywalking.apm.plugin.jedis.v2.JedisMethodInterceptor.*;

@RunWith(MockitoJUnitRunner.class)
public class JedisConstructorWithStringArgInterceptorTest {

    private JedisConstructorWithStringArgInterceptor interceptor;

    @Mock
    private EnhancedClassInstanceContext instanceContext;
    @Mock
    private ConstructorInvokeContext invokeContext;

    @Before
    public void setUp() throws Exception {
        interceptor = new JedisConstructorWithStringArgInterceptor();
        when(invokeContext.allArguments()).thenReturn(new Object[] {"127.0.0.1"});
    }

    @Test
    public void onConstruct() throws Exception {
        interceptor.onConstruct(instanceContext, invokeContext);

        verify(instanceContext, times(1)).set(KEY_OF_REDIS_CONN_INFO, "127.0.0.1:6379");
        verify(instanceContext, times(1)).set(KEY_OF_REDIS_HOST, "127.0.0.1");
        verify(instanceContext, times(1)).set(KEY_OF_REDIS_PORT, 6379);
    }

    @Test
    public void onConstructWithPort() {
        when(invokeContext.allArguments()).thenReturn(new Object[] {"127.0.0.1", 16379});
        interceptor.onConstruct(instanceContext, invokeContext);

        verify(instanceContext, times(1)).set(KEY_OF_REDIS_CONN_INFO, "127.0.0.1:16379");
        verify(instanceContext, times(1)).set(KEY_OF_REDIS_HOST, "127.0.0.1");
        verify(instanceContext, times(1)).set(KEY_OF_REDIS_PORT, 16379);
    }

}
