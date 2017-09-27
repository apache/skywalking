package org.skywalking.apm.plugin.xmemcached.v2;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

@RunWith(MockitoJUnitRunner.class)
public class XMemcachedConstructorWithComplexArgInterceptorTest {

	private XMemcachedConstructorWithComplexArgInterceptor interceptor;

	@Mock
	private EnhancedInstance enhancedInstance;

	@Before
	public void setUp() throws Exception {
		interceptor = new XMemcachedConstructorWithComplexArgInterceptor();
	}

	@Test
	public void onConstructWithComplex() {
		Map<InetSocketAddress, InetSocketAddress> inetSocketAddressMap = new HashMap<InetSocketAddress, InetSocketAddress>();
		inetSocketAddressMap.put(new InetSocketAddress("127.0.0.1", 11211), new InetSocketAddress("127.0.0.2", 11211));
		inetSocketAddressMap.put(new InetSocketAddress("127.0.0.1", 11212), new InetSocketAddress("127.0.0.3", 11211));
		interceptor.onConstruct(enhancedInstance, new Object[] { inetSocketAddressMap  });

		verify(enhancedInstance, times(1)).setSkyWalkingDynamicField("127.0.0.1:11211;127.0.0.2:11211;127.0.0.3:11211;");
	}
}
