package org.skywalking.apm.plugin.xmemcached.v2;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

@RunWith(MockitoJUnitRunner.class)
public class XMemcachedConstructorWithInetSocketAddressArgInterceptorTest {

	private XMemcachedConstructorWithInetSocketAddressArgInterceptor interceptor;

	@Mock
	private EnhancedInstance enhancedInstance;

	@Before
	public void setUp() throws Exception {
		interceptor = new XMemcachedConstructorWithInetSocketAddressArgInterceptor();
	}

	@Test
	public void onConstructWithInetSocketAddress() {
		interceptor.onConstruct(enhancedInstance, new Object[] {  new InetSocketAddress("127.0.0.1", 11211) });

		verify(enhancedInstance, times(1)).setSkyWalkingDynamicField("127.0.0.1:11211");
	}
}
