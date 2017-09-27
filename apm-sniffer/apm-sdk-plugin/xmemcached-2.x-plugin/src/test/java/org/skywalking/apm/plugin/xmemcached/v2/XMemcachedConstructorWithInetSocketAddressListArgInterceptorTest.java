package org.skywalking.apm.plugin.xmemcached.v2;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

@RunWith(MockitoJUnitRunner.class)
public class XMemcachedConstructorWithInetSocketAddressListArgInterceptorTest {

	private XMemcachedConstructorWithInetSocketAddressListArgInterceptor interceptor;

	@Mock
	private EnhancedInstance enhancedInstance;

	@Before
	public void setUp() throws Exception {
		interceptor = new XMemcachedConstructorWithInetSocketAddressListArgInterceptor();
	}

	@Test
	public void onConstructWithInetSocketAddressList() {
		List<InetSocketAddress> inetSocketAddressList = new ArrayList<InetSocketAddress>();
		inetSocketAddressList.add(new InetSocketAddress("127.0.0.1", 11211));
		inetSocketAddressList.add(new InetSocketAddress("127.0.0.2", 11211));
		interceptor.onConstruct(enhancedInstance, new Object[] { inetSocketAddressList  });

		verify(enhancedInstance, times(1)).setSkyWalkingDynamicField("127.0.0.1:11211;127.0.0.2:11211;");
	}
}
