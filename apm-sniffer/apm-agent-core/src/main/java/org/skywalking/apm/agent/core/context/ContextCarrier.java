package org.skywalking.apm.agent.core.context;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.agent.core.context.ids.DistributedTraceId;
import org.skywalking.apm.agent.core.context.ids.PropagatedTraceId;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
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
    private String traceSegmentId;

    private int spanId = -1;

    private String applicationCode;

    private String peerHost;

    /**
     * {@link DistributedTraceId}
     */
    private List<DistributedTraceId> distributedTraceIds;

    /**
     * Serialize this {@link ContextCarrier} to a {@link String},
     * with '|' split.
     *
     * @return the serialization string.
     */
    public String serialize() {
        if (this.isValid()) {
            return StringUtil.join('|',
                this.getTraceSegmentId(),
                this.getSpanId() + "",
                this.getApplicationCode(),
                this.getPeerHost(),
                this.serializeDistributedTraceIds());
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
            String[] parts = text.split("\\|", 6);
            if (parts.length == 5) {
                try {
                    setSpanId(Integer.parseInt(parts[1]));
                    setTraceSegmentId(parts[0]);
                    setApplicationCode(parts[2]);
                    setPeerHost(parts[3]);
                    setDistributedTraceIds(deserializeDistributedTraceIds(parts[4]));
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
        return !StringUtil.isEmpty(traceSegmentId)
            && getSpanId() > -1
            && !StringUtil.isEmpty(applicationCode)
            && !StringUtil.isEmpty(peerHost)
            && distributedTraceIds != null;
    }

    public String getTraceSegmentId() {
        return traceSegmentId;
    }

    public int getSpanId() {
        return spanId;
    }

    public void setTraceSegmentId(String traceSegmentId) {
        this.traceSegmentId = traceSegmentId;
    }

    public void setSpanId(int spanId) {
        this.spanId = spanId;
    }

    public String getApplicationCode() {
        return applicationCode;
    }

    public void setApplicationCode(String applicationCode) {
        this.applicationCode = applicationCode;
    }

    public void setApplicationId(int applicationId) {
        this.applicationCode = applicationId + "";
    }

    public String getPeerHost() {
        return peerHost;
    }

    public void setPeerHost(String peerHost) {
        this.peerHost = peerHost;
    }

    public void setPeerId(int peerId) {
        this.peerHost = peerId + "";
    }

    public List<DistributedTraceId> getDistributedTraceIds() {
        return distributedTraceIds;
    }

    public void setDistributedTraceIds(List<DistributedTraceId> distributedTraceIds) {
        this.distributedTraceIds = distributedTraceIds;
    }

    /**
     * Serialize {@link #distributedTraceIds} to a string, with ',' split.
     *
     * @return string, represents all {@link DistributedTraceId}
     */
    private String serializeDistributedTraceIds() {
        StringBuilder traceIdString = new StringBuilder();
        if (distributedTraceIds != null) {
            boolean first = true;
            for (DistributedTraceId distributedTraceId : distributedTraceIds) {
                if (first) {
                    first = false;
                } else {
                    traceIdString.append(",");
                }
                traceIdString.append(distributedTraceId.get());
            }
        }
        return traceIdString.toString();
    }

    /**
     * Deserialize {@link #distributedTraceIds} from a text, whith
     *
     * @param text
     * @return
     */
    private List<DistributedTraceId> deserializeDistributedTraceIds(String text) {
        if (StringUtil.isEmpty(text)) {
            return null;
        }
        String[] propagationTraceIdValues = text.split(",");
        List<DistributedTraceId> traceIds = new LinkedList<DistributedTraceId>();
        for (String propagationTraceIdValue : propagationTraceIdValues) {
            traceIds.add(new PropagatedTraceId(propagationTraceIdValue));
        }
        return traceIds;
    }

}
