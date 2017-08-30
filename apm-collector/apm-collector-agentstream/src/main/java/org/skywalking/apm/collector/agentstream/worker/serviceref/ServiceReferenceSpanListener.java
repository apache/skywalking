package org.skywalking.apm.collector.agentstream.worker.serviceref;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.cache.InstanceCache;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.ExitSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.RefsListener;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.serviceref.ServiceReferenceDataDefine;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.network.proto.SpanLayer;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.TraceSegmentReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ServiceReferenceSpanListener implements FirstSpanListener, EntrySpanListener, ExitSpanListener, RefsListener {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceSpanListener.class);

    private List<ServiceReferenceDataDefine.ServiceReference> exitServiceRefs = new ArrayList<>();
    private List<TraceSegmentReference> referenceServices = new ArrayList<>();
    private int serviceId = 0;
    private String serviceName = "";
    private long startTime = 0;
    private long endTime = 0;
    private boolean isError = false;
    private long timeBucket;

    @Override
    public void parseFirst(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanObject.getStartTime());
    }

    @Override public void parseRef(TraceSegmentReference reference, int applicationId, int applicationInstanceId,
        String segmentId) {
        referenceServices.add(reference);
    }

    @Override
    public void parseEntry(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        serviceId = spanObject.getOperationNameId();
        if (spanObject.getOperationNameId() == 0) {
            serviceName = String.valueOf(applicationId) + Const.ID_SPLIT + spanObject.getOperationName();
        } else {
            serviceName = Const.EMPTY_STRING;
        }
        startTime = spanObject.getStartTime();
        endTime = spanObject.getEndTime();
        isError = spanObject.getIsError();
    }

    @Override
    public void parseExit(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        if (spanObject.getSpanLayer().equals(SpanLayer.Database)) {
            ServiceReferenceDataDefine.ServiceReference serviceReference = new ServiceReferenceDataDefine.ServiceReference();
            serviceReference.setBehindServiceId(spanObject.getOperationNameId());
            if (spanObject.getOperationNameId() == 0) {
                serviceReference.setBehindServiceName(String.valueOf(applicationId) + Const.ID_SPLIT + spanObject.getOperationName());
            } else {
                serviceReference.setBehindServiceName(Const.EMPTY_STRING);
            }
            calculateCost(serviceReference, spanObject.getStartTime(), spanObject.getEndTime(), spanObject.getIsError());
            exitServiceRefs.add(serviceReference);
        }
    }

    private void calculateCost(ServiceReferenceDataDefine.ServiceReference serviceReference, long startTime,
        long endTime,
        boolean isError) {
        long cost = endTime - startTime;
        if (cost <= 1000 && !isError) {
            serviceReference.setS1Lte(1);
        } else if (1000 < cost && cost <= 3000 && !isError) {
            serviceReference.setS3Lte(1);
        } else if (3000 < cost && cost <= 5000 && !isError) {
            serviceReference.setS5Lte(1);
        } else if (5000 < cost && !isError) {
            serviceReference.setS5Gt(1);
        } else {
            serviceReference.setError(1);
        }
        serviceReference.setSummary(1);
        serviceReference.setCostSummary(cost);
    }

    @Override public void build() {
        logger.debug("service reference listener build");
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        if (referenceServices.size() > 0) {
            referenceServices.forEach(reference -> {
                ServiceReferenceDataDefine.ServiceReference serviceReference = new ServiceReferenceDataDefine.ServiceReference();
                int entryServiceId = reference.getEntryServiceId();
                String entryServiceName = buildServiceName(reference.getEntryApplicationInstanceId(), reference.getEntryServiceId(), reference.getEntryServiceName());

                int frontServiceId = reference.getParentServiceId();
                String frontServiceName = buildServiceName(reference.getParentApplicationInstanceId(), reference.getParentServiceId(), reference.getParentServiceName());

                int behindServiceId = serviceId;
                String behindServiceName = serviceName;

                calculateCost(serviceReference, startTime, endTime, isError);

                logger.debug("has reference, entryServiceId: {}, entryServiceName: {}", entryServiceId, entryServiceName);
                sendToAggregationWorker(context, serviceReference, entryServiceId, entryServiceName, frontServiceId, frontServiceName, behindServiceId, behindServiceName);
            });
        } else {
            ServiceReferenceDataDefine.ServiceReference serviceReference = new ServiceReferenceDataDefine.ServiceReference();
            int entryServiceId = serviceId;
            String entryServiceName = serviceName;

            int frontServiceId = Const.NONE_SERVICE_ID;
            String frontServiceName = Const.EMPTY_STRING;

            int behindServiceId = serviceId;
            String behindServiceName = serviceName;

            calculateCost(serviceReference, startTime, endTime, isError);
            sendToAggregationWorker(context, serviceReference, entryServiceId, entryServiceName, frontServiceId, frontServiceName, behindServiceId, behindServiceName);
        }

        exitServiceRefs.forEach(serviceReference -> {
            if (referenceServices.size() > 0) {
                referenceServices.forEach(reference -> {
                    int entryServiceId = reference.getEntryServiceId();
                    String entryServiceName = buildServiceName(reference.getEntryApplicationInstanceId(), reference.getEntryServiceId(), reference.getEntryServiceName());

                    int frontServiceId = reference.getParentServiceId();
                    String frontServiceName = buildServiceName(reference.getParentApplicationInstanceId(), reference.getParentServiceId(), reference.getParentServiceName());

                    int behindServiceId = serviceReference.getBehindServiceId();
                    String behindServiceName = serviceReference.getBehindServiceName();
                    sendToAggregationWorker(context, serviceReference, entryServiceId, entryServiceName, frontServiceId, frontServiceName, behindServiceId, behindServiceName);
                });
            } else {
                int entryServiceId = serviceId;
                String entryServiceName = serviceName;

                int frontServiceId = serviceId;
                String frontServiceName = serviceName;

                int behindServiceId = serviceReference.getBehindServiceId();
                String behindServiceName = serviceReference.getBehindServiceName();
                sendToAggregationWorker(context, serviceReference, entryServiceId, entryServiceName, frontServiceId, frontServiceName, behindServiceId, behindServiceName);
            }
        });
    }

    private void sendToAggregationWorker(StreamModuleContext context,
        ServiceReferenceDataDefine.ServiceReference serviceReference, int entryServiceId, String entryServiceName,
        int frontServiceId, String frontServiceName, int behindServiceId, String behindServiceName) {

        StringBuilder idBuilder = new StringBuilder();
        idBuilder.append(timeBucket).append(Const.ID_SPLIT);
        if (entryServiceId == 0) {
            idBuilder.append(entryServiceName).append(Const.ID_SPLIT);
            serviceReference.setEntryServiceId(0);
            serviceReference.setEntryServiceName(entryServiceName);
        } else {
            idBuilder.append(entryServiceId).append(Const.ID_SPLIT);
            serviceReference.setEntryServiceId(entryServiceId);
            serviceReference.setEntryServiceName(Const.EMPTY_STRING);
        }

        if (frontServiceId == 0) {
            idBuilder.append(frontServiceName).append(Const.ID_SPLIT);
            serviceReference.setFrontServiceId(0);
            serviceReference.setFrontServiceName(frontServiceName);
        } else {
            idBuilder.append(frontServiceId).append(Const.ID_SPLIT);
            serviceReference.setFrontServiceId(frontServiceId);
            serviceReference.setFrontServiceName(Const.EMPTY_STRING);
        }

        if (behindServiceId == 0) {
            idBuilder.append(behindServiceName);
            serviceReference.setBehindServiceId(0);
            serviceReference.setBehindServiceName(behindServiceName);
        } else {
            idBuilder.append(behindServiceId);
            serviceReference.setBehindServiceId(behindServiceId);
            serviceReference.setBehindServiceName(Const.EMPTY_STRING);
        }

        serviceReference.setId(idBuilder.toString());
        serviceReference.setTimeBucket(timeBucket);
        try {
            logger.debug("send to service reference aggregation worker, id: {}", serviceReference.getId());
            context.getClusterWorkerContext().lookup(ServiceReferenceAggregationWorker.WorkerRole.INSTANCE).tell(serviceReference.toData());
        } catch (WorkerInvokeException | WorkerNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private String buildServiceName(int instanceId, int serviceId, String serviceName) {
        if (serviceId == 0) {
            int applicationId = InstanceCache.get(instanceId);
            return String.valueOf(applicationId) + Const.ID_SPLIT + serviceName;
        } else {
            return Const.EMPTY_STRING;
        }
    }
}
