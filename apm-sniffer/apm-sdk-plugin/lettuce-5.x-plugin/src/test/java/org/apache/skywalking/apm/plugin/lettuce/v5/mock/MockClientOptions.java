package org.apache.skywalking.apm.plugin.lettuce.v5.mock;

import io.lettuce.core.cluster.ClusterClientOptions;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

public class MockClientOptions extends ClusterClientOptions implements EnhancedInstance {

    private Object object;

    public MockClientOptions() {
        this(ClusterClientOptions.builder());
    }

    protected MockClientOptions(Builder builder) {
        super(builder);
    }

    @Override
    public Object getSkyWalkingDynamicField() {
        return object;
    }

    @Override
    public void setSkyWalkingDynamicField(Object value) {
        this.object = value;
    }
}
