package org.skywalking.apm.collector.agentstream.worker.serviceref.reference;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.agentstream.worker.cache.InstanceCache;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.ExitSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.RefsListener;
import org.skywalking.apm.collector.storage.define.serviceref.ServiceRefDataDefine;
import org.skywalking.apm.collector.agentstream.worker.util.ExchangeMarkUtils;
import org.skywalking.apm.collector.stream.worker.util.TimeBucketUtils;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.TraceSegmentReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ServiceRefSpanListener implements FirstSpanListener, EntrySpanListener, ExitSpanListener, RefsListener {

    private final Logger logger = LoggerFactory.getLogger(ServiceRefSpanListener.class);

    private List<String> exitServiceNames = new ArrayList<>();
    private String currentServiceName;
    private List<ServiceTemp> referenceServices = new ArrayList<>();
    private boolean hasReference = false;
    private long timeBucket;

    @Override
    public void parseFirst(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanObject.getStartTime());
    }

    @Override public void parseRef(TraceSegmentReference reference, int applicationId, int applicationInstanceId,
        String segmentId) {
        int entryApplicationId = InstanceCache.get(reference.getEntryApplicationInstanceId());
        String entryServiceName = reference.getEntryServiceName();
        if (reference.getEntryServiceId() != 0) {
            entryServiceName = ExchangeMarkUtils.INSTANCE.buildMarkedID(reference.getEntryServiceId());
        }
        entryServiceName = ExchangeMarkUtils.INSTANCE.buildMarkedID(entryApplicationId) + Const.ID_SPLIT + entryServiceName;

        int parentApplicationId = InstanceCache.get(reference.getParentApplicationInstanceId());
        String parentServiceName = ExchangeMarkUtils.INSTANCE.buildMarkedID(reference.getParentServiceId());
        if (reference.getParentServiceId() == 0) {
            parentServiceName = reference.getParentServiceName();
        }
        parentServiceName = ExchangeMarkUtils.INSTANCE.buildMarkedID(parentApplicationId) + Const.ID_SPLIT + parentServiceName;

        referenceServices.add(new ServiceTemp(entryServiceName, parentServiceName));
        hasReference = true;
    }

    @Override
    public void parseEntry(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        String serviceName = spanObject.getOperationName();
        if (spanObject.getOperationNameId() != 0) {
            serviceName = ExchangeMarkUtils.INSTANCE.buildMarkedID(spanObject.getOperationNameId());
        }

        serviceName = ExchangeMarkUtils.INSTANCE.buildMarkedID(applicationId) + Const.ID_SPLIT + serviceName;
        currentServiceName = serviceName;
    }

    @Override
    public void parseExit(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        String serviceName = ExchangeMarkUtils.INSTANCE.buildMarkedID(spanObject.getOperationNameId());
        if (spanObject.getOperationNameId() == 0) {
            serviceName = spanObject.getOperationName();
        }
        serviceName = ExchangeMarkUtils.INSTANCE.buildMarkedID(applicationId) + Const.ID_SPLIT + serviceName;
        exitServiceNames.add(serviceName);
    }

    @Override public void build() {
        logger.debug("service reference listener build");
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        List<ServiceRefDataDefine.ServiceReference> serviceReferences = new ArrayList<>();
        for (ServiceTemp referenceService : referenceServices) {
            String agg = referenceService.parentServiceName + Const.ID_SPLIT + currentServiceName;
            ServiceRefDataDefine.ServiceReference serviceReference = new ServiceRefDataDefine.ServiceReference();
            serviceReference.setId(timeBucket + Const.ID_SPLIT + referenceService.entryServiceName + Const.ID_SPLIT + agg);
            serviceReference.setEntryService(referenceService.entryServiceName);
            serviceReference.setAgg(agg);
            serviceReference.setTimeBucket(timeBucket);

            serviceReferences.add(serviceReference);
        }

        for (String exitServiceName : exitServiceNames) {
            String entryServiceName;
            if (referenceServices.size() > 0) {
                entryServiceName = referenceServices.get(0).entryServiceName;
            } else {
                entryServiceName = currentServiceName;
            }

            String agg = currentServiceName + Const.ID_SPLIT + exitServiceName;
            ServiceRefDataDefine.ServiceReference serviceReference = new ServiceRefDataDefine.ServiceReference();
            serviceReference.setId(timeBucket + Const.ID_SPLIT + entryServiceName + Const.ID_SPLIT + agg);
            serviceReference.setEntryService(entryServiceName);
            serviceReference.setAgg(agg);
            serviceReference.setTimeBucket(timeBucket);

            serviceReferences.add(serviceReference);
        }

        for (ServiceRefDataDefine.ServiceReference serviceReference : serviceReferences) {
            try {
                logger.debug("send to service reference aggregation worker, id: {}", serviceReference.getId());
                context.getClusterWorkerContext().lookup(ServiceRefAggregationWorker.WorkerRole.INSTANCE).tell(serviceReference.toData());
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    class ServiceTemp {
        private final String entryServiceName;
        private final String parentServiceName;

        public ServiceTemp(String entryServiceName, String parentServiceName) {
            this.entryServiceName = entryServiceName;
            this.parentServiceName = parentServiceName;
        }
    }
}
