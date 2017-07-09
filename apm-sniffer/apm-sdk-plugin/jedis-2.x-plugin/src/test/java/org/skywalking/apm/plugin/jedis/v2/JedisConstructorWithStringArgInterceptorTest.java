package org.skywalking.apm.plugin.jedis.v2;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class JedisConstructorWithStringArgInterceptorTest {

    private JedisConstructorWithStringArgInterceptor interceptor;

    @Mock
    private EnhancedInstance enhancedInstance;

    @Before
    public void setUp() throws Exception {
        interceptor = new JedisConstructorWithStringArgInterceptor();
    }

    @Test
    public void onConstruct() throws Exception {
        interceptor.onConstruct(enhancedInstance, new Object[] {"127.0.0.1"});

        verify(enhancedInstance, times(1)).setSkyWalkingDynamicField("127.0.0.1:6379");
    }

    @Test
    public void onConstructWithPort() {
        interceptor.onConstruct(enhancedInstance, new Object[] {"127.0.0.1", 16379});

        verify(enhancedInstance, times(1)).setSkyWalkingDynamicField("127.0.0.1:16379");
    }

}
