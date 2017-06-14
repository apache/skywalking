package org.skywalking.apm.collector.worker.httpserver;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorker;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.AbstractWorker;
import org.skywalking.apm.collector.actor.LocalSyncWorkerRef;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;

/**
 * The <code>AbstractPostProvider</code> implementations represent providers, which use to create {@link AbstractPost}
 * worker instances.
 *
 * @author pengys5
 * @since v3.0-2017
 */
public abstract class AbstractPostProvider<T extends AbstractLocalSyncWorker> extends AbstractLocalSyncWorkerProvider<T> {

    public abstract String servletPath();

    /**
     * Create worker instance, http servlet and set the worker reference into servlet.
     *
     * @param context use to create a mapping between url and worker.
     * @throws IllegalArgumentException
     * @throws ProviderNotFoundException
     */
    final protected void create(
        ServletContextHandler context) throws IllegalArgumentException, ProviderNotFoundException {
        LocalSyncWorkerRef workerRef = (LocalSyncWorkerRef)super.create(AbstractWorker.noOwner());
        AbstractPost.PostWithHttpServlet postWithHttpServlet = new AbstractPost.PostWithHttpServlet(workerRef);
        context.addServlet(new ServletHolder(postWithHttpServlet), servletPath());
    }
}
