package org.skywalking.apm.agent.core.context.trace;

import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextSnapshot;
import org.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.skywalking.apm.network.proto.RefType;
import org.skywalking.apm.network.proto.TraceSegmentReference;

/**
 * {@link TraceSegmentRef} is like a pointer, which ref to another {@link TraceSegment},
 * use {@link #spanId} point to the exact span of the ref {@link TraceSegment}.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public class TraceSegmentRef {
    private SegmentRefType type;

    private String traceSegmentId;

    private int spanId = -1;

    private int applicationInstanceId;

    private String peerHost;

    private int peerId = DictionaryUtil.nullValue();

    private String operationName;

    private int operationId = DictionaryUtil.nullValue();

    /**
     * Transform a {@link ContextCarrier} to the <code>TraceSegmentRef</code>
     *
     * @param carrier the valid cross-process propagation format.
     */
    public TraceSegmentRef(ContextCarrier carrier) {
        this.type = SegmentRefType.CROSS_PROCESS;
        this.traceSegmentId = carrier.getTraceSegmentId();
        this.spanId = carrier.getSpanId();
        this.applicationInstanceId = carrier.getApplicationInstanceId();
        String host = carrier.getPeerHost();
        if (host.charAt(0) == '#') {
            this.peerHost = host.substring(1);
        } else {
            this.peerId = Integer.parseInt(host);
        }
        String entryOperationName = carrier.getEntryOperationName();
        if (entryOperationName.charAt(0) == '#') {
            this.operationName = entryOperationName.substring(1);
        } else {
            this.operationId = Integer.parseInt(entryOperationName);
        }
    }

    public TraceSegmentRef(ContextSnapshot snapshot) {
        this.type = SegmentRefType.CROSS_THREAD;
        this.traceSegmentId = snapshot.getTraceSegmentId();
        this.spanId = snapshot.getSpanId();
    }

    public String getOperationName() {
        return operationName;
    }

    public int getOperationId() {
        return operationId;
    }

    public TraceSegmentReference transform() {
        TraceSegmentReference.Builder refBuilder = TraceSegmentReference.newBuilder();
        if (SegmentRefType.CROSS_PROCESS.equals(type)) {
            refBuilder.setRefType(RefType.CrossProcess);
            refBuilder.setParentApplicationInstanceId(applicationInstanceId);
            if (peerId == DictionaryUtil.nullValue()) {
                refBuilder.setNetworkAddress(peerHost);
            } else {
                refBuilder.setNetworkAddressId(peerId);
            }
            if (operationId == DictionaryUtil.nullValue()) {
                refBuilder.setEntryServiceName(operationName);
            } else {
                refBuilder.setEntryServiceId(operationId);
            }
        } else {
            refBuilder.setRefType(RefType.CrossThread);
        }
        refBuilder.setParentTraceSegmentId(traceSegmentId);
        refBuilder.setParentSpanId(spanId);

        return refBuilder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        TraceSegmentRef ref = (TraceSegmentRef)o;

        if (spanId != ref.spanId)
            return false;
        return traceSegmentId.equals(ref.traceSegmentId);
    }

    @Override
    public int hashCode() {
        int result = traceSegmentId.hashCode();
        result = 31 * result + spanId;
        return result;
    }

    public enum SegmentRefType {
        CROSS_PROCESS,
        CROSS_THREAD
    }
}
