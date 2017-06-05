package org.skywalking.apm.collector.worker.httpserver;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.skywalking.apm.collector.actor.*;

/**
 * @author pengys5
 */
public abstract class AbstractPostProvider<T extends AbstractLocalSyncWorker> extends AbstractLocalSyncWorkerProvider<T> {

    public abstract String servletPath();

    final protected void create(
        ServletContextHandler context) throws IllegalArgumentException, ProviderNotFoundException {
        WorkerRef workerRef = super.create(AbstractWorker.noOwner());
        context.addServlet(new ServletHolder(handleServlet(workerRef)), servletPath());
    }

    public abstract AbstractPostWithHttpServlet handleServlet(WorkerRef workerRef);
}
