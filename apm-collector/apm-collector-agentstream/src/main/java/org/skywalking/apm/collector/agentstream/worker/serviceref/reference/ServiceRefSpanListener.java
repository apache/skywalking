package org.skywalking.apm.collector.agentstream.worker.serviceref.reference;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.Const;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.ExitSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.RefsListener;
import org.skywalking.apm.collector.agentstream.worker.serviceref.reference.define.ServiceRefDataDefine;
import org.skywalking.apm.collector.agentstream.worker.util.TimeBucketUtils;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.TraceSegmentReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ServiceRefSpanListener implements FirstSpanListener, EntrySpanListener, ExitSpanListener, RefsListener {

    private final Logger logger = LoggerFactory.getLogger(ServiceRefSpanListener.class);

    private String front;
    private List<String> behinds = new ArrayList<>();
    private List<ServiceTemp> fronts = new ArrayList<>();
    private long timeBucket;

    @Override public void parseFirst(SpanObject spanObject, int applicationId, int applicationInstanceId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanObject.getStartTime());
    }

    @Override public void parseRef(TraceSegmentReference reference, int applicationId, int applicationInstanceId) {
        String entryService = String.valueOf(reference.getEntryServiceId());
        if (reference.getEntryServiceId() == 0) {
            entryService = reference.getEntryServiceName();
        }
        String parentService = String.valueOf(reference.getParentServiceId());
        if (reference.getParentServiceId() == 0) {
            parentService = reference.getParentServiceName();
        }
        fronts.add(new ServiceTemp(entryService, parentService));
    }

    @Override public void parseEntry(SpanObject spanObject, int applicationId, int applicationInstanceId) {
        front = String.valueOf(spanObject.getOperationNameId());
        if (spanObject.getOperationNameId() == 0) {
            front = spanObject.getOperationName();
        }
    }

    @Override public void parseExit(SpanObject spanObject, int applicationId, int applicationInstanceId) {
        String behind = String.valueOf(spanObject.getOperationNameId());
        if (spanObject.getOperationNameId() == 0) {
            behind = spanObject.getOperationName();
        }
        behinds.add(behind);
    }

    @Override public void build() {
        for (String behind : behinds) {
            String agg = front + Const.ID_SPLIT + behind;
            ServiceRefDataDefine.ServiceReference serviceReference = new ServiceRefDataDefine.ServiceReference();
            serviceReference.setId(timeBucket + Const.ID_SPLIT + agg);
            serviceReference.setAgg(agg);
            serviceReference.setTimeBucket(timeBucket);
        }
    }

    class ServiceTemp {
        private final String entryService;
        private final String parentService;

        public ServiceTemp(String entryService, String parentService) {
            this.entryService = entryService;
            this.parentService = parentService;
        }
    }
}
