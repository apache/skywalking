package org.skywalking.apm.collector.actor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public abstract class AbstractWorker {

    private final Logger logger;

    private final LocalWorkerContext selfContext;

    private final Role role;

    private final ClusterWorkerContext clusterContext;

    public AbstractWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        this.role = role;
        this.clusterContext = clusterContext;
        this.selfContext = selfContext;
        this.logger = LogManager.getFormatterLogger(role.roleName());
    }

    final public Logger logger() {
        return logger;
    }

    public abstract void preStart() throws ProviderNotFoundException;

    final public LookUp getSelfContext() {
        return selfContext;
    }

    final public LookUp getClusterContext() {
        return clusterContext;
    }

    final public Role getRole() {
        return role;
    }

    final public static AbstractWorker noOwner() {
        return null;
    }

    final protected void saveException(Exception e) {
        logger().error(e);
    }
}
