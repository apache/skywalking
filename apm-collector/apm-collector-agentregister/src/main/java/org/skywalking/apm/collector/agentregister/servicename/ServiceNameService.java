package org.skywalking.apm.collector.agentregister.servicename;

import org.skywalking.apm.collector.storage.define.register.ServiceNameDataDefine;
import org.skywalking.apm.collector.agentregister.worker.servicename.ServiceNameRegisterRemoteWorker;
import org.skywalking.apm.collector.agentregister.worker.servicename.dao.IServiceNameDAO;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ServiceNameService {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameService.class);

    public int getOrCreate(int applicationId, String serviceName) {
        IServiceNameDAO dao = (IServiceNameDAO)DAOContainer.INSTANCE.get(IServiceNameDAO.class.getName());
        int serviceId = dao.getServiceId(applicationId, serviceName);

        if (serviceId == 0) {
            StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);
            ServiceNameDataDefine.ServiceName service = new ServiceNameDataDefine.ServiceName("0", serviceName, applicationId, 0);
            try {
                context.getClusterWorkerContext().lookup(ServiceNameRegisterRemoteWorker.WorkerRole.INSTANCE).tell(service);
            } catch (WorkerNotFoundException | WorkerInvokeException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return serviceId;
    }
}
