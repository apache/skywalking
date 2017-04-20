package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * @author pengys5
 */
public abstract class AbstractGetProvider<T extends AbstractLocalSyncWorker> extends AbstractLocalSyncWorkerProvider<T> {

    public abstract String servletPath();

    final protected void create(
        ServletContextHandler context) throws IllegalArgumentException, ProviderNotFoundException {
        LocalSyncWorkerRef workerRef = (LocalSyncWorkerRef)super.create(AbstractWorker.noOwner());
        AbstractGet.GetWithHttpServlet getWithHttpServlet = new AbstractGet.GetWithHttpServlet(workerRef);
        context.addServlet(new ServletHolder(getWithHttpServlet), servletPath());
    }
}
