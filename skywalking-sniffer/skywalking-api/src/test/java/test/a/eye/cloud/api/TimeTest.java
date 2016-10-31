package test.a.eye.cloud.api;

import org.junit.Test;

import com.a.eye.skywalking.invoke.monitor.RPCClientInvokeMonitor;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.model.Identification.IdentificationBuilder;

public class TimeTest {
	@Test
	public void test(){
		RPCClientInvokeMonitor sender = new RPCClientInvokeMonitor();
		long start = System.currentTimeMillis();
		for (int i = 0; i < 100; i++) {
			IdentificationBuilder builder = Identification
					.newBuilder()
					.viewPoint("1111");
			sender.beforeInvoke(builder.build());
			sender.afterInvoke();
		}
		long end = System.currentTimeMillis();
		System.out.println(end - start + "ms");
	}
	
}
