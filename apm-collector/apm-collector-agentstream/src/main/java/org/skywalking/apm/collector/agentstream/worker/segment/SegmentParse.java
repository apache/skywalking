package org.skywalking.apm.collector.agentstream.worker.segment;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.global.GlobalTraceSpanListener;
import org.skywalking.apm.collector.agentstream.worker.node.component.NodeComponentSpanListener;
import org.skywalking.apm.collector.agentstream.worker.node.mapping.NodeMappingSpanListener;
import org.skywalking.apm.collector.agentstream.worker.noderef.reference.NodeRefSpanListener;
import org.skywalking.apm.collector.agentstream.worker.noderef.summary.NodeRefSumSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.cost.SegmentCostSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.origin.SegmentPersistenceWorker;
import org.skywalking.apm.collector.agentstream.worker.segment.origin.define.SegmentDataDefine;
import org.skywalking.apm.collector.agentstream.worker.service.entry.ServiceEntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.serviceref.reference.ServiceRefSpanListener;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.SpanType;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.TraceSegmentReference;
import org.skywalking.apm.network.proto.UniqueId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class SegmentParse {

    private final Logger logger = LoggerFactory.getLogger(SegmentParse.class);

    private List<SpanListener> spanListeners;

    public SegmentParse() {
        spanListeners = new ArrayList<>();
        spanListeners.add(new NodeRefSpanListener());
        spanListeners.add(new NodeComponentSpanListener());
        spanListeners.add(new NodeMappingSpanListener());
        spanListeners.add(new NodeRefSumSpanListener());
        spanListeners.add(new SegmentCostSpanListener());
        spanListeners.add(new GlobalTraceSpanListener());
        spanListeners.add(new ServiceEntrySpanListener());
        spanListeners.add(new ServiceRefSpanListener());
    }

    public void parse(List<UniqueId> traceIds, TraceSegmentObject segmentObject) {
        StringBuilder segmentIdBuilder = new StringBuilder();
        segmentObject.getTraceSegmentId().getIdPartsList().forEach(part -> {
            segmentIdBuilder.append(part);
        });
        String segmentId = segmentIdBuilder.toString();

        for (UniqueId uniqueId : traceIds) {
            notifyGlobalsListener(uniqueId);
        }

        int applicationId = segmentObject.getApplicationId();
        int applicationInstanceId = segmentObject.getApplicationInstanceId();

        for (TraceSegmentReference reference : segmentObject.getRefsList()) {
            notifyRefsListener(reference, applicationId, applicationInstanceId, segmentId);
        }

        List<SpanObject> spans = segmentObject.getSpansList();
        if (CollectionUtils.isNotEmpty(spans)) {
            for (SpanObject spanObject : spans) {
                if (spanObject.getSpanId() == 0) {
                    notifyFirstListener(spanObject, applicationId, applicationInstanceId, segmentId);
                }

                if (SpanType.Exit.equals(spanObject.getSpanType())) {
                    notifyExitListener(spanObject, applicationId, applicationInstanceId, segmentId);
                } else if (SpanType.Entry.equals(spanObject.getSpanType())) {
                    notifyEntryListener(spanObject, applicationId, applicationInstanceId, segmentId);
                } else if (SpanType.Local.equals(spanObject.getSpanType())) {
                    notifyLocalListener(spanObject, applicationId, applicationInstanceId, segmentId);
                } else {
                    logger.error("span type error, span type: {}", spanObject.getSpanType().name());
                }
            }
        }

        notifyListenerToBuild();
        buildSegment(segmentId, segmentObject.toByteArray());
    }

    public void buildSegment(String id, byte[] dataBinary) {
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);
        SegmentDataDefine.Segment segment = new SegmentDataDefine.Segment();
        segment.setId(id);
        segment.setDataBinary(dataBinary);

        try {
            logger.debug("send to segment persistence worker, id: {}, dataBinary length: {}", segment.getId(), dataBinary.length);
            context.getClusterWorkerContext().lookup(SegmentPersistenceWorker.WorkerRole.INSTANCE).tell(segment.toData());
        } catch (WorkerInvokeException | WorkerNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void notifyListenerToBuild() {
        spanListeners.forEach(listener -> listener.build());
    }

    private void notifyExitListener(SpanObject spanObject, int applicationId, int applicationInstanceId,
        String segmentId) {
        for (SpanListener listener : spanListeners) {
            if (listener instanceof ExitSpanListener) {
                ((ExitSpanListener)listener).parseExit(spanObject, applicationId, applicationInstanceId, segmentId);
            }
        }
    }

    private void notifyEntryListener(SpanObject spanObject, int applicationId, int applicationInstanceId,
        String segmentId) {
        for (SpanListener listener : spanListeners) {
            if (listener instanceof EntrySpanListener) {
                ((EntrySpanListener)listener).parseEntry(spanObject, applicationId, applicationInstanceId, segmentId);
            }
        }
    }

    private void notifyLocalListener(SpanObject spanObject, int applicationId, int applicationInstanceId,
        String segmentId) {
        for (SpanListener listener : spanListeners) {
            if (listener instanceof LocalSpanListener) {
                ((LocalSpanListener)listener).parseLocal(spanObject, applicationId, applicationInstanceId, segmentId);
            }
        }
    }

    private void notifyFirstListener(SpanObject spanObject, int applicationId, int applicationInstanceId,
        String segmentId) {
        for (SpanListener listener : spanListeners) {
            if (listener instanceof FirstSpanListener) {
                ((FirstSpanListener)listener).parseFirst(spanObject, applicationId, applicationInstanceId, segmentId);
            }
        }
    }

    private void notifyRefsListener(TraceSegmentReference reference, int applicationId, int applicationInstanceId,
        String segmentId) {
        for (SpanListener listener : spanListeners) {
            if (listener instanceof RefsListener) {
                ((RefsListener)listener).parseRef(reference, applicationId, applicationInstanceId, segmentId);
            }
        }
    }

    private void notifyGlobalsListener(UniqueId uniqueId) {
        for (SpanListener listener : spanListeners) {
            if (listener instanceof GlobalTraceIdsListener) {
                ((GlobalTraceIdsListener)listener).parseGlobalTraceId(uniqueId);
            }
        }
    }
}
