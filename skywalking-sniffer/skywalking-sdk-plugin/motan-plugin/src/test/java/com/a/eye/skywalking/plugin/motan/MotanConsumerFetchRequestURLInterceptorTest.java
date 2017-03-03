package com.a.eye.skywalking.plugin.motan;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.weibo.api.motan.rpc.URL;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MotanConsumerFetchRequestURLInterceptorTest {

    private MotanConsumerFetchRequestURLInterceptor requestURLInterceptor;

    @Mock
    private EnhancedClassInstanceContext instanceContext;
    @Mock
    private InstanceMethodInvokeContext interceptorContext;

    private URL url;

    @Before
    public void setUp() {
        requestURLInterceptor = new MotanConsumerFetchRequestURLInterceptor();
        url = URL.valueOf("motan://127.0.0.0.1:34000/com.a.eye.skywalking.test.TestService");

        when(interceptorContext.allArguments()).thenReturn(new Object[]{url});
    }

    @Test
    public void testFetchRequestURL() {
        requestURLInterceptor.beforeMethod(instanceContext, interceptorContext, null);
        requestURLInterceptor.afterMethod(instanceContext, interceptorContext, null);

        verify(instanceContext, times(1)).set(Matchers.any(), Matchers.any());
    }

    @Test
    public void testFetchRequestURLWithException(){
        requestURLInterceptor.beforeMethod(instanceContext, interceptorContext, null);
        requestURLInterceptor.handleMethodException(new RuntimeException(), instanceContext, interceptorContext);
        requestURLInterceptor.afterMethod(instanceContext, interceptorContext, null);

        verify(instanceContext, times(1)).set(Matchers.any(), Matchers.any());
    }
}