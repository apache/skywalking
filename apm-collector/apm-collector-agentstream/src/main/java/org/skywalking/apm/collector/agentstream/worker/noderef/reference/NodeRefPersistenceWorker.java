package org.skywalking.apm.collector.agentstream.worker.noderef.reference;

import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.agentstream.worker.noderef.reference.dao.INodeReferenceDAO;
import org.skywalking.apm.collector.agentstream.worker.noderef.reference.define.NodeRefDataDefine;
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
public class NodeRefPersistenceWorker extends PersistenceWorker {

    public NodeRefPersistenceWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected List<?> prepareBatch(Map<String, Data> dataMap) {
        INodeReferenceDAO dao = (INodeReferenceDAO)DAOContainer.INSTANCE.get(INodeReferenceDAO.class.getName());
        return dao.prepareBatch(dataMap);
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeRefPersistenceWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeRefPersistenceWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefPersistenceWorker(role(), clusterContext);
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
            return NodeRefPersistenceWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }

        @Override public DataDefine dataDefine() {
            return new NodeRefDataDefine();
        }
    }
}
