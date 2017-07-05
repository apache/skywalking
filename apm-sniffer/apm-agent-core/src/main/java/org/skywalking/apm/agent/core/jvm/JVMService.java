package org.skywalking.apm.agent.core.jvm;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.jvm.cpu.CPUProvider;
import org.skywalking.apm.agent.core.jvm.memory.MemoryProvider;
import org.skywalking.apm.agent.core.jvm.memorypool.MemoryPoolProvider;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;
import org.skywalking.apm.network.proto.JVMMetric;
import org.skywalking.apm.network.proto.MemoryPool;

/**
 * @author wusheng
 */
public class JVMService implements BootService, Runnable {
    private static ILog logger = LogManager.getLogger(JVMService.class);
    private SimpleDateFormat sdf = new SimpleDateFormat("ss");
    private volatile ScheduledFuture<?> scheduledFuture;
    private volatile int lastSeconds = -1;

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
        int secondInt = Integer.parseInt(second);
        if (secondInt % 15 == 0 && secondInt != lastSeconds) {
            lastSeconds = secondInt;
            try {
                JVMMetric.Builder JVMBuilder = JVMMetric.newBuilder();
                JVMBuilder.setTime(currentTimeMillis);
                JVMBuilder.setCpu(CPUProvider.INSTANCE.getCpuMetric());
                JVMBuilder.addAllMemory(MemoryProvider.INSTANCE.getMemoryMetricList());
                List<MemoryPool> memoryPoolMetricList = MemoryPoolProvider.INSTANCE.getMemoryPoolMetricList();
                if (memoryPoolMetricList.size() > 0) {
                    JVMBuilder.addAllMemoryPool(memoryPoolMetricList);
                }
            } catch (Exception e) {
                logger.error(e, "Collect JVM info fail.");
            }
        }
    }
}
