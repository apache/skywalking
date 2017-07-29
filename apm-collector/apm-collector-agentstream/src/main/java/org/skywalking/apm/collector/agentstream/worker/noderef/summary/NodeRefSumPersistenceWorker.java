package org.skywalking.apm.collector.agentstream.worker.noderef.summary;

import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.agentstream.worker.noderef.summary.dao.INodeRefSumDAO;
import org.skywalking.apm.collector.agentstream.worker.noderef.summary.define.NodeRefSumDataDefine;
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
public class NodeRefSumPersistenceWorker extends PersistenceWorker {

    public NodeRefSumPersistenceWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected List<?> prepareBatch(Map<String, Data> dataMap) {
        INodeRefSumDAO dao = (INodeRefSumDAO)DAOContainer.INSTANCE.get(INodeRefSumDAO.class.getName());
        return dao.prepareBatch(dataMap);
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeRefSumPersistenceWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeRefSumPersistenceWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefSumPersistenceWorker(role(), clusterContext);
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
            return NodeRefSumPersistenceWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }

        @Override public DataDefine dataDefine() {
            return new NodeRefSumDataDefine();
        }
    }
}
