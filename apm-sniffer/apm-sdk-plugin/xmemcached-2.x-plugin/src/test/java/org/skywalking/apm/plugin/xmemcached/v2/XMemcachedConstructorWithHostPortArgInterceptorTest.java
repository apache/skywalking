package org.skywalking.apm.plugin.xmemcached.v2;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

@RunWith(MockitoJUnitRunner.class)
public class XMemcachedConstructorWithHostPortArgInterceptorTest {

	private XMemcachedConstructorWithHostPortArgInterceptor interceptor;

	@Mock
	private EnhancedInstance enhancedInstance;

	@Before
	public void setUp() throws Exception {
		interceptor = new XMemcachedConstructorWithHostPortArgInterceptor();
	}

	@Test
	public void onConstructWithHostPort() {
		interceptor.onConstruct(enhancedInstance, new Object[] { "127.0.0.1", 11211 });

		verify(enhancedInstance, times(1)).setSkyWalkingDynamicField("127.0.0.1:11211");
	}
}
