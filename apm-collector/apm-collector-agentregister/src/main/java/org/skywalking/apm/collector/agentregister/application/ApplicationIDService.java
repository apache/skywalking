package org.skywalking.apm.collector.agentregister.application;

import org.skywalking.apm.collector.agentregister.worker.application.ApplicationRegisterRemoteWorker;
import org.skywalking.apm.collector.agentregister.worker.cache.ApplicationCache;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.storage.define.register.ApplicationDataDefine;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ApplicationIDService {

    private final Logger logger = LoggerFactory.getLogger(ApplicationIDService.class);

    public int getOrCreate(String applicationCode) {
        int applicationId = ApplicationCache.get(applicationCode);

        if (applicationId == 0) {
            StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);
            ApplicationDataDefine.Application application = new ApplicationDataDefine.Application(applicationCode, applicationCode, 0);
            try {
                context.getClusterWorkerContext().lookup(ApplicationRegisterRemoteWorker.WorkerRole.INSTANCE).tell(application);
            } catch (WorkerNotFoundException | WorkerInvokeException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return applicationId;
    }
}
