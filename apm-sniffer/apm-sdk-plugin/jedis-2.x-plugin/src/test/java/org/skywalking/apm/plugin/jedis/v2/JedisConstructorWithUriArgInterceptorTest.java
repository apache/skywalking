package org.skywalking.apm.plugin.jedis.v2;

import java.net.URI;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(PowerMockRunner.class)
@PrepareForTest(URI.class)
public class JedisConstructorWithUriArgInterceptorTest {

    private JedisConstructorWithUriArgInterceptor interceptor;

    @Mock
    private EnhancedInstance enhancedInstance;
    private URI uri = URI.create("http://127.0.0.1:6379");

    @Before
    public void setUp() throws Exception {
        interceptor = new JedisConstructorWithUriArgInterceptor();
    }

    @Test
    public void onConstruct() throws Exception {
        interceptor.onConstruct(enhancedInstance, new Object[] {uri});

        verify(enhancedInstance, times(1)).setSkyWalkingDynamicField("127.0.0.1:6379");
    }
}
