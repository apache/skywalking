package org.apache.skywalking.oap.server.analyzer.provider.golang;

import org.apache.skywalking.apm.network.language.agent.v3.GolangMetric;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceGolangStack;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

public class GolangSourceDispatcher {
    private final SourceReceiver sourceReceiver;

    public GolangSourceDispatcher(ModuleManager moduleManager) {
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
    }

    public void sendMetric(String service, String serviceInstance, GolangMetric golangMetric) {
        long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(golangMetric.getTime());

        final String serviceId = IDManager.ServiceID.buildId(service, true);
        final String serviceInstanceId = IDManager.ServiceInstanceID.buildId(serviceId, serviceInstance);

        this.sendToStackProcess(service, serviceId, serviceInstance, serviceInstanceId, minuteTimeBucket, golangMetric.getStackInUse());
    }

    private void sendToStackProcess(String service,
                                   String serviceId,
                                   String serviceInstance,
                                   String serviceInstanceId,
                                   long timeBucket,
                                   long heapUsed) {
        ServiceInstanceGolangStack serviceInstanceGolangStack = new ServiceInstanceGolangStack();
        serviceInstanceGolangStack.setId(serviceInstanceId);
        serviceInstanceGolangStack.setName(serviceInstance);
        serviceInstanceGolangStack.setServiceId(serviceId);
        serviceInstanceGolangStack.setServiceName(service);
        serviceInstanceGolangStack.setTimeBucket(timeBucket);
        serviceInstanceGolangStack.setUsed(heapUsed);
        this.sourceReceiver.receive(serviceInstanceGolangStack);
    }


}
