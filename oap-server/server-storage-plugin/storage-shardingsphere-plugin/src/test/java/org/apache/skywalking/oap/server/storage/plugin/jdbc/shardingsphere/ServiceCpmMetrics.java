package org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere;

import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.CPMMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

@Stream(
    name = "service_cpm",
    scopeId = 1,
    builder = ServiceCpmMetricsBuilder.class,
    processor = MetricsStreamProcessor.class
)
public class ServiceCpmMetrics extends CPMMetrics {
    public static final String INDEX_NAME = "service_cpm";
    @Column(
        columnName = "entity_id",
        length = 512
    )
    private String entityId;

    public ServiceCpmMetrics() {
    }

    public String getEntityId() {
        return this.entityId;
    }

    public void setEntityId(String var1) {
        this.entityId = var1;
    }

    protected String id0() {
        StringBuilder var1 = new StringBuilder(String.valueOf(this.getTimeBucket()));
        var1.append("_").append(this.entityId);
        return var1.toString();
    }

    public int hashCode() {
        byte var1 = 17;
        int var2 = 31 * var1 + this.entityId.hashCode();
        var2 = 31 * var2 + (int) this.getTimeBucket();
        return var2;
    }

    public int remoteHashCode() {
        byte var1 = 17;
        int var2 = 31 * var1 + this.entityId.hashCode();
        return var2;
    }

    public boolean equals(Object var1) {
        if (this == var1) {
            return true;
        } else if (var1 == null) {
            return false;
        } else if (this.getClass() != var1.getClass()) {
            return false;
        } else {
            ServiceCpmMetrics var2 = (ServiceCpmMetrics) var1;
            if (!this.entityId.equals(var2.entityId)) {
                return false;
            } else {
                return this.getTimeBucket() == var2.getTimeBucket();
            }
        }
    }

    public RemoteData.Builder serialize() {
        RemoteData.Builder var1 = RemoteData.newBuilder();
        var1.addDataStrings(this.getEntityId());
        var1.addDataLongs(this.getValue());
        var1.addDataLongs(this.getTotal());
        var1.addDataLongs(this.getTimeBucket());
        return var1;
    }

    public void deserialize(RemoteData var1) {
        this.setEntityId(var1.getDataStrings(0));
        this.setValue(var1.getDataLongs(0));
        this.setTotal(var1.getDataLongs(1));
        this.setTimeBucket(var1.getDataLongs(2));
    }

    public MetricsMetaInfo getMeta() {
        return new MetricsMetaInfo("service_cpm", 1, this.entityId);
    }

    public Metrics toHour() {
        ServiceCpmMetrics var1 = new ServiceCpmMetrics();
        var1.setEntityId(this.getEntityId());
        var1.setValue(this.getValue());
        var1.setTotal(this.getTotal());
        var1.setTimeBucket(this.toTimeBucketInHour());
        return var1;
    }

    public Metrics toDay() {
        ServiceCpmMetrics var1 = new ServiceCpmMetrics();
        var1.setEntityId(this.getEntityId());
        var1.setValue(this.getValue());
        var1.setTotal(this.getTotal());
        var1.setTimeBucket(this.toTimeBucketInDay());
        return var1;
    }
}
