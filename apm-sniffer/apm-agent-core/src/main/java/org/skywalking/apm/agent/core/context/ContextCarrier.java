package org.skywalking.apm.agent.core.context;

import java.io.Serializable;
import java.util.List;
import org.skywalking.apm.agent.core.context.ids.DistributedTraceId;
import org.skywalking.apm.agent.core.context.ids.ID;
import org.skywalking.apm.agent.core.context.ids.PropagatedTraceId;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.skywalking.apm.util.StringUtil;

/**
 * {@link ContextCarrier} is a data carrier of {@link TracingContext}.
 * It holds the snapshot (current state) of {@link TracingContext}.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public class ContextCarrier implements Serializable {
    /**
     * {@link TraceSegment#traceSegmentId}
     */
    private ID traceSegmentId;

    private int spanId = -1;

    private int applicationInstanceId = DictionaryUtil.nullValue();

    private String peerHost;

    private String entryOperationName;

    private String parentOperationName;

    /**
     * {@link DistributedTraceId}
     */
    private DistributedTraceId primaryDistributedTraceId;

    /**
     * Serialize this {@link ContextCarrier} to a {@link String},
     * with '|' split.
     *
     * @return the serialization string.
     */
    public String serialize() {
        if (this.isValid()) {
            return StringUtil.join('|',
                this.getTraceSegmentId().toBase64(),
                this.getSpanId() + "",
                this.getApplicationInstanceId() + "",
                this.getPeerHost(),
                this.getEntryOperationName(),
                this.getParentOperationName(),
                this.getPrimaryDistributedTraceId());
        } else {
            return "";
        }
    }

    /**
     * Initialize fields with the given text.
     *
     * @param text carries {@link #traceSegmentId} and {@link #spanId}, with '|' split.
     */
    public ContextCarrier deserialize(String text) {
        if (text != null) {
            String[] parts = text.split("\\|", 7);
            if (parts.length == 7) {
                try {
                    this.traceSegmentId = new ID(parts[0]);
                    this.spanId = Integer.parseInt(parts[1]);
                    this.applicationInstanceId = Integer.parseInt(parts[2]);
                    this.peerHost = parts[3];
                    this.entryOperationName = parts[4];
                    this.parentOperationName = parts[5];
                    this.primaryDistributedTraceId = new PropagatedTraceId(parts[6]);
                } catch (NumberFormatException e) {

                }
            }
        }
        return this;
    }

    /**
     * Make sure this {@link ContextCarrier} has been initialized.
     *
     * @return true for unbroken {@link ContextCarrier} or no-initialized. Otherwise, false;
     */
    public boolean isValid() {
        return traceSegmentId != null
            && getSpanId() > -1
            && applicationInstanceId != DictionaryUtil.nullValue()
            && !StringUtil.isEmpty(peerHost)
            && !StringUtil.isEmpty(entryOperationName)
            && !StringUtil.isEmpty(parentOperationName)
            && primaryDistributedTraceId != null;
    }

    public String getEntryOperationName() {
        return entryOperationName;
    }

    void setEntryOperationName(String entryOperationName) {
        this.entryOperationName = '#' + entryOperationName;
    }

    void setEntryOperationId(int entryOperationId) {
        this.entryOperationName = entryOperationId + "";
    }

    void setParentOperationName(String parentOperationName) {
        this.parentOperationName = '#' + parentOperationName;
    }

    void setParentOperationId(int parentOperationId) {
        this.parentOperationName = parentOperationId + "";
    }

    public ID getTraceSegmentId() {
        return traceSegmentId;
    }

    public int getSpanId() {
        return spanId;
    }

    void setTraceSegmentId(ID traceSegmentId) {
        this.traceSegmentId = traceSegmentId;
    }

    void setSpanId(int spanId) {
        this.spanId = spanId;
    }

    public int getApplicationInstanceId() {
        return applicationInstanceId;
    }

    void setApplicationInstanceId(int applicationInstanceId) {
        this.applicationInstanceId = applicationInstanceId;
    }

    public String getPeerHost() {
        return peerHost;
    }

    void setPeerHost(String peerHost) {
        this.peerHost = '#' + peerHost;
    }

    void setPeerId(int peerId) {
        this.peerHost = peerId + "";
    }

    public DistributedTraceId getDistributedTraceId() {
        return primaryDistributedTraceId;
    }

    public void setDistributedTraceIds(List<DistributedTraceId> distributedTraceIds) {
        this.primaryDistributedTraceId = distributedTraceIds.get(0);
    }

    private String getPrimaryDistributedTraceId() {
        return primaryDistributedTraceId.toString();
    }

    public String getParentOperationName() {
        return parentOperationName;
    }
}
