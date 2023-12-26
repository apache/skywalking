package org.apache.skywalking.oap.server.core.storage.query;

import java.util.List;
import org.apache.skywalking.oap.server.core.hierarchy.instance.InstanceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.hierarchy.service.ServiceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.storage.DAO;

public interface IHierarchyQueryDAO extends DAO {
    List<ServiceHierarchyRelationTraffic> readAllServiceHierarchyRelations() throws Exception;

    /**
     * Return the given instance's hierarchy.
     */
    List<InstanceHierarchyRelationTraffic> readInstanceHierarchyRelations(String instanceId, String layer) throws Exception;
}
