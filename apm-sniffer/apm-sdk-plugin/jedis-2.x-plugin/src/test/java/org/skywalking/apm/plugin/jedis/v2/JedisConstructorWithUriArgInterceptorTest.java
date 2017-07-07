package org.skywalking.apm.plugin.jedis.v2;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.net.URI;

import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(URI.class)
public class JedisConstructorWithUriArgInterceptorTest {

    private JedisConstructorWithUriArgInterceptor interceptor;

    @Mock
    private EnhancedClassInstanceContext instanceContext;
    @Mock
    private ConstructorInvokeContext invokeContext;
    private URI uri = URI.create("http://127.0.0.1:6379");

    @Before
    public void setUp() throws Exception {
        interceptor = new JedisConstructorWithUriArgInterceptor();

        when(invokeContext.allArguments()).thenReturn(new Object[] {uri});
    }

    @Test
    public void onConstruct() throws Exception {
        interceptor.onConstruct(instanceContext, invokeContext);

        verify(instanceContext, times(1)).set(JedisMethodInterceptor.KEY_OF_REDIS_CONN_INFO, "127.0.0.1:6379");
        verify(instanceContext, times(1)).set(JedisMethodInterceptor.KEY_OF_REDIS_HOST, "127.0.0.1");
        verify(instanceContext, times(1)).set(JedisMethodInterceptor.KEY_OF_REDIS_PORT, 6379);
    }
}
