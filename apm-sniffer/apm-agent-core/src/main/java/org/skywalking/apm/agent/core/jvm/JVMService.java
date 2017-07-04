package org.skywalking.apm.agent.core.jvm;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.jvm.cpu.CPUProvider;
import org.skywalking.apm.agent.core.jvm.memory.MemoryProvider;
import org.skywalking.apm.agent.core.jvm.memorypool.MemoryPoolProvider;
import org.skywalking.apm.network.proto.JVMMetric;

/**
 * @author wusheng
 */
public class JVMService implements BootService, Runnable {
    private SimpleDateFormat sdf = new SimpleDateFormat("ss");
    private volatile ScheduledFuture<?> scheduledFuture;

    @Override
    public void beforeBoot() throws Throwable {

    }

    @Override
    public void boot() throws Throwable {
        ScheduledExecutorService service = Executors
            .newSingleThreadScheduledExecutor();
        scheduledFuture = service.scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void afterBoot() throws Throwable {

    }

    @Override
    public void run() {
        long currentTimeMillis = System.currentTimeMillis();
        Date day = new Date(currentTimeMillis);
        String second = sdf.format(day);

        if (Integer.parseInt(second) % 15 == 0) {
            JVMMetric.Builder JVMBuilder = JVMMetric.newBuilder();
            JVMBuilder.setTime(currentTimeMillis);
            JVMBuilder.setCpu(CPUProvider.INSTANCE.getCpuMetric());
            JVMBuilder.addAllMemory(MemoryProvider.INSTANCE.getMemoryMetricList());
            JVMBuilder.addAllMemoryPool(MemoryPoolProvider.INSTANCE.getMemoryPoolMetricList());
        }
    }
}
