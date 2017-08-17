package org.skywalking.apm.collector.core.storage;

import java.util.List;
import java.util.Map;

/**
 * @author pengys5
 */
public abstract class StorageBatchBuilder<B, C, D> {
    public abstract List<B> build(C client, Map<String, D> lastData);
}
