package org.skywalking.apm.collector.worker.httpserver;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.skywalking.apm.collector.actor.*;

/**
 * @author pengys5
 */
public abstract class AbstractPostProvider<T extends AbstractLocalAsyncWorker> extends AbstractLocalAsyncWorkerProvider<T> {

    public abstract String servletPath();

    final protected void create(
        ServletContextHandler context) throws IllegalArgumentException, ProviderNotFoundException {
        LocalAsyncWorkerRef workerRef = (LocalAsyncWorkerRef) super.create(AbstractWorker.noOwner());
        AbstractPost.PostWithHttpServlet postWithHttpServlet = new AbstractPost.PostWithHttpServlet(workerRef);
        context.addServlet(new ServletHolder(postWithHttpServlet), servletPath());
    }
}
