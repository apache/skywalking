package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * @author pengys5
 */
public abstract class AbstractSearcherProvider<T extends AbstractLocalSyncWorker> extends AbstractLocalSyncWorkerProvider<T> {

    public abstract String servletPath();

    final protected void createSearcher(ServletContextHandler context) throws IllegalArgumentException, ProviderNotFoundException {
        LocalSyncWorkerRef workerRef = (LocalSyncWorkerRef) super.create(AbstractWorker.noOwner());
        AbstractSearcher.SearchWithHttpServlet searchWithHttpServlet = new AbstractSearcher.SearchWithHttpServlet(workerRef);
        context.addServlet(new ServletHolder(searchWithHttpServlet), servletPath());
    }
}
