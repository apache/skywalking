package test.ai.cloud.api;

import org.junit.Test;

import com.ai.cloud.skywalking.invoke.monitor.RPCClientInvokeMonitor;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.model.Identification.IdentificationBuilder;

public class TimeTest {
	@Test
	public void test(){
		RPCClientInvokeMonitor sender = new RPCClientInvokeMonitor();
		long start = System.currentTimeMillis();
		for (int i = 0; i < 100; i++) {
			IdentificationBuilder builder = Identification
					.newBuilder()
					.viewPoint("1111");
			sender.traceBeforeInvoke(builder.build());
			sender.afterInvoke();
		}
		long end = System.currentTimeMillis();
		System.out.println(end - start + "ms");
	}
	
}
