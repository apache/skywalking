package org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere;

import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

public class ServiceCpmMetricsBuilder implements StorageBuilder {
    public ServiceCpmMetricsBuilder() {
    }

    public void entity2Storage(StorageData var1, Convert2Storage var2) {
        ServiceCpmMetrics var3 = (ServiceCpmMetrics) var1;
        var2.accept("entity_id", var3.getEntityId());
        var2.accept("value", new Long(var3.getValue()));
        var2.accept("total", new Long(var3.getTotal()));
        var2.accept("time_bucket", new Long(var3.getTimeBucket()));
    }

    public StorageData storage2Entity(Convert2Entity var1) {
        ServiceCpmMetrics var2 = new ServiceCpmMetrics();
        var2.setEntityId((String) var1.get("entity_id"));
        var2.setValue(((Number) var1.get("value")).longValue());
        var2.setTotal(((Number) var1.get("total")).longValue());
        var2.setTimeBucket(((Number) var1.get("time_bucket")).longValue());
        return var2;
    }
}
