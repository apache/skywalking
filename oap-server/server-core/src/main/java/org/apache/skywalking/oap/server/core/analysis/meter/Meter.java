package org.apache.skywalking.oap.server.core.analysis.meter;

import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo;
import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;

/**
 * Meter is the abstract parent for all {@link org.apache.skywalking.oap.server.core.analysis.meter.function.MeterFunction} annotated functions.
 * It provides the {@link WithMetadata} implementation for alarm kernal.
 *
 * @since 9.0.0
 */
public abstract class Meter extends Metrics implements WithMetadata {
    protected MetricsMetaInfo metadata = new MetricsMetaInfo("UNKNOWN", DefaultScopeDefine.UNKNOWN);

    /**
     * This method is called in {@link MeterSystem#create} process through dynamic Java codes.
     *
     * @param metricName metric name
     * @param scopeId    scope Id defined in {@link DefaultScopeDefine}
     */
    public void initMeta(String metricName, int scopeId) {
        this.metadata.setMetricsName(metricName);
        this.metadata.setScope(scopeId);
    }

    public MetricsMetaInfo getMeta() {
        // Only read the id from the implementation when needed, to avoid uninitialized cases.
        this.metadata.setId(this.id());
        return metadata;
    }
}
