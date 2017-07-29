package org.skywalking.apm.collector.agentstream.worker.segment;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.node.component.NodeComponentSpanListener;
import org.skywalking.apm.collector.agentstream.worker.node.mapping.NodeMappingSpanListener;
import org.skywalking.apm.collector.agentstream.worker.noderef.reference.NodeRefSpanListener;
import org.skywalking.apm.collector.core.util.CollectionUtils;
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
    private List<SpanListener> refsListeners;

    public SegmentParse() {
        spanListeners = new ArrayList<>();
        spanListeners.add(new NodeRefSpanListener());
        spanListeners.add(new NodeComponentSpanListener());
        spanListeners.add(new NodeMappingSpanListener());

        refsListeners = new ArrayList<>();
    }

    public void parse(List<UniqueId> traceIds, TraceSegmentObject segmentObject) {
        for (UniqueId uniqueId : traceIds) {
            uniqueId.getIdPartsList();
        }

        int applicationId = segmentObject.getApplicationId();
        int applicationInstanceId = segmentObject.getApplicationInstanceId();

        for (TraceSegmentReference reference : segmentObject.getRefsList()) {
            notifyRefsListener(reference, applicationId, applicationInstanceId);
        }

        List<SpanObject> spans = segmentObject.getSpansList();
        if (CollectionUtils.isNotEmpty(spans)) {
            for (SpanObject spanObject : spans) {
                if (spanObject.getSpanId() == 0) {
                    notifyFirstListener(spanObject, applicationId, applicationInstanceId);
                }

                if (SpanType.Exit.equals(spanObject.getSpanType())) {
                    notifyExitListener(spanObject, applicationId, applicationInstanceId);
                } else if (SpanType.Entry.equals(spanObject.getSpanType())) {
                    notifyEntryListener(spanObject, applicationId, applicationInstanceId);
                } else if (SpanType.Local.equals(spanObject.getSpanType())) {
                    notifyLocalListener(spanObject, applicationId, applicationInstanceId);
                } else {
                    logger.error("span type error, span type: {}", spanObject.getSpanType().name());
                }
            }
        }

        notifyListenerToBuild();
    }

    private void notifyListenerToBuild() {
        spanListeners.forEach(listener -> listener.build());
        refsListeners.forEach(listener -> listener.build());
    }

    private void notifyExitListener(SpanObject spanObject, int applicationId, int applicationInstanceId) {
        for (SpanListener listener : spanListeners) {
            if (listener instanceof ExitSpanListener) {
                ((ExitSpanListener)listener).parseExit(spanObject, applicationId, applicationInstanceId);
            }
        }
    }

    private void notifyEntryListener(SpanObject spanObject, int applicationId, int applicationInstanceId) {
        for (SpanListener listener : spanListeners) {
            if (listener instanceof EntrySpanListener) {
                ((EntrySpanListener)listener).parseEntry(spanObject, applicationId, applicationInstanceId);
            }
        }
    }

    private void notifyLocalListener(SpanObject spanObject, int applicationId, int applicationInstanceId) {
        for (SpanListener listener : spanListeners) {
            if (listener instanceof LocalSpanListener) {
                ((LocalSpanListener)listener).parseLocal(spanObject, applicationId, applicationInstanceId);
            }
        }
    }

    private void notifyFirstListener(SpanObject spanObject, int applicationId, int applicationInstanceId) {
        for (SpanListener listener : spanListeners) {
            if (listener instanceof FirstSpanListener) {
                ((FirstSpanListener)listener).parseFirst(spanObject, applicationId, applicationInstanceId);
            }
        }
    }

    private void notifyRefsListener(TraceSegmentReference reference, int applicationId, int applicationInstanceId) {
        for (SpanListener listener : refsListeners) {
            if (listener instanceof RefsListener) {
                ((RefsListener)listener).parseRef(reference, applicationId, applicationInstanceId);
            }
        }
    }
}
