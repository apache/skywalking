/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentstream.worker.segment;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.global.GlobalTraceSpanListener;
import org.skywalking.apm.collector.agentstream.worker.instance.performance.InstPerformanceSpanListener;
import org.skywalking.apm.collector.agentstream.worker.node.component.NodeComponentSpanListener;
import org.skywalking.apm.collector.agentstream.worker.node.mapping.NodeMappingSpanListener;
import org.skywalking.apm.collector.agentstream.worker.noderef.NodeReferenceSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.cost.SegmentCostSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.origin.SegmentPersistenceWorker;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.ReferenceDecorator;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.ReferenceIdExchanger;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.SegmentDecorator;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.SegmentStandardizationWorker;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.SpanDecorator;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.SpanIdExchanger;
import org.skywalking.apm.collector.agentstream.worker.service.entry.ServiceEntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.serviceref.ServiceReferenceSpanListener;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.storage.define.segment.SegmentDataDefine;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.network.proto.SpanType;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.UniqueId;
import org.skywalking.apm.network.proto.UpstreamSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentParse {

    private final Logger logger = LoggerFactory.getLogger(SegmentParse.class);

    private List<SpanListener> spanListeners;
    private String segmentId;

    public SegmentParse() {
        spanListeners = new ArrayList<>();
        spanListeners.add(new NodeComponentSpanListener());
        spanListeners.add(new NodeMappingSpanListener());
        spanListeners.add(new NodeReferenceSpanListener());
        spanListeners.add(new SegmentCostSpanListener());
        spanListeners.add(new GlobalTraceSpanListener());
        spanListeners.add(new ServiceEntrySpanListener());
        spanListeners.add(new ServiceReferenceSpanListener());
        spanListeners.add(new InstPerformanceSpanListener());
    }

    public boolean parse(UpstreamSegment segment, Source source) {
        try {
            List<UniqueId> traceIds = segment.getGlobalTraceIdsList();
            TraceSegmentObject segmentObject = TraceSegmentObject.parseFrom(segment.getSegment());

            SegmentDecorator segmentDecorator = new SegmentDecorator(segmentObject);
            if (!preBuild(traceIds, segmentDecorator)) {
                logger.debug("This segment id exchange not success, write to buffer file, id: {}", segmentId);

                if (source.equals(Source.Agent)) {
                    writeToBufferFile(segmentId, segment);
                }
                return false;
            } else {
                logger.debug("This segment id exchange success, id: {}", segmentId);
                notifyListenerToBuild();
                buildSegment(segmentId, segmentDecorator.toByteArray());
                return true;
            }
        } catch (InvalidProtocolBufferException e) {
            logger.error(e.getMessage(), e);
        }
        return false;
    }

    private boolean preBuild(List<UniqueId> traceIds, SegmentDecorator segmentDecorator) {
        StringBuilder segmentIdBuilder = new StringBuilder();

        for (int i = 0; i < segmentDecorator.getTraceSegmentId().getIdPartsList().size(); i++) {
            if (i == 0) {
                segmentIdBuilder.append(segmentDecorator.getTraceSegmentId().getIdPartsList().get(i));
            } else {
                segmentIdBuilder.append(".").append(segmentDecorator.getTraceSegmentId().getIdPartsList().get(i));
            }
        }

        segmentId = segmentIdBuilder.toString();

        for (UniqueId uniqueId : traceIds) {
            notifyGlobalsListener(uniqueId);
        }

        int applicationId = segmentDecorator.getApplicationId();
        int applicationInstanceId = segmentDecorator.getApplicationInstanceId();

        for (int i = 0; i < segmentDecorator.getRefsCount(); i++) {
            ReferenceDecorator referenceDecorator = segmentDecorator.getRefs(i);
            if (!ReferenceIdExchanger.getInstance().exchange(referenceDecorator, applicationId)) {
                return false;
            }

            notifyRefsListener(referenceDecorator, applicationId, applicationInstanceId, segmentId);
        }

        for (int i = 0; i < segmentDecorator.getSpansCount(); i++) {
            SpanDecorator spanDecorator = segmentDecorator.getSpans(i);

            if (!SpanIdExchanger.getInstance().exchange(spanDecorator, applicationId)) {
                return false;
            }

            if (spanDecorator.getSpanId() == 0) {
                notifyFirstListener(spanDecorator, applicationId, applicationInstanceId, segmentId);
            }

            if (SpanType.Exit.equals(spanDecorator.getSpanType())) {
                notifyExitListener(spanDecorator, applicationId, applicationInstanceId, segmentId);
            } else if (SpanType.Entry.equals(spanDecorator.getSpanType())) {
                notifyEntryListener(spanDecorator, applicationId, applicationInstanceId, segmentId);
            } else if (SpanType.Local.equals(spanDecorator.getSpanType())) {
                notifyLocalListener(spanDecorator, applicationId, applicationInstanceId, segmentId);
            } else {
                logger.error("span type value was unexpected, span type name: {}", spanDecorator.getSpanType().name());
            }
        }

        return true;
    }

    private void buildSegment(String id, byte[] dataBinary) {
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

    private void writeToBufferFile(String id, UpstreamSegment upstreamSegment) {
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        try {
            logger.debug("send to segment buffer write worker, id: {}", id);
            context.getClusterWorkerContext().lookup(SegmentStandardizationWorker.WorkerRole.INSTANCE).tell(upstreamSegment);
        } catch (WorkerInvokeException | WorkerNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void notifyListenerToBuild() {
        spanListeners.forEach(SpanListener::build);
    }

    private void notifyExitListener(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        for (SpanListener listener : spanListeners) {
            if (listener instanceof ExitSpanListener) {
                ((ExitSpanListener)listener).parseExit(spanDecorator, applicationId, applicationInstanceId, segmentId);
            }
        }
    }

    private void notifyEntryListener(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        for (SpanListener listener : spanListeners) {
            if (listener instanceof EntrySpanListener) {
                ((EntrySpanListener)listener).parseEntry(spanDecorator, applicationId, applicationInstanceId, segmentId);
            }
        }
    }

    private void notifyLocalListener(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        for (SpanListener listener : spanListeners) {
            if (listener instanceof LocalSpanListener) {
                ((LocalSpanListener)listener).parseLocal(spanDecorator, applicationId, applicationInstanceId, segmentId);
            }
        }
    }

    private void notifyFirstListener(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        for (SpanListener listener : spanListeners) {
            if (listener instanceof FirstSpanListener) {
                ((FirstSpanListener)listener).parseFirst(spanDecorator, applicationId, applicationInstanceId, segmentId);
            }
        }
    }

    private void notifyRefsListener(ReferenceDecorator reference, int applicationId, int applicationInstanceId,
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

    public enum Source {
        Agent, Buffer
    }
}
