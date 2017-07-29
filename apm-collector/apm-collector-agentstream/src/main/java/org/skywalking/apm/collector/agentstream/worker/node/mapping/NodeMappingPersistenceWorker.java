package org.skywalking.apm.collector.agentstream.worker.node.mapping;

import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.agentstream.worker.node.mapping.dao.INodeMappingDAO;
import org.skywalking.apm.collector.agentstream.worker.node.mapping.define.NodeMappingDataDefine;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.impl.PersistenceWorker;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.selector.HashCodeSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class NodeMappingPersistenceWorker extends PersistenceWorker {

    public NodeMappingPersistenceWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected List<?> prepareBatch(Map<String, Data> dataMap) {
        INodeMappingDAO dao = (INodeMappingDAO)DAOContainer.INSTANCE.get(INodeMappingDAO.class.getName());
        return dao.prepareBatch(dataMap);
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeMappingPersistenceWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeMappingPersistenceWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeMappingPersistenceWorker(role(), clusterContext);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeMappingPersistenceWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }

        @Override public DataDefine dataDefine() {
            return new NodeMappingDataDefine();
        }
    }
}
