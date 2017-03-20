package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * @author pengys5
 */
public abstract class AbstractReceiverProvider<T extends AbstractLocalAsyncWorker> extends AbstractLocalAsyncWorkerProvider<T> {

    public abstract String servletPath();

    final protected void createReceiver(ServletContextHandler context) throws IllegalArgumentException, ProviderNotFoundException {
        LocalAsyncWorkerRef workerRef = (LocalAsyncWorkerRef) super.create(AbstractWorker.noOwner());
        AbstractReceiver.ReceiveWithHttpServlet receiveWithHttpServlet = new AbstractReceiver.ReceiveWithHttpServlet(workerRef);
        context.addServlet(new ServletHolder(receiveWithHttpServlet), servletPath());
    }

    @Override
    final public T workerInstance(ClusterWorkerContext clusterContext) {
        return null;
    }

    @Override
    final public Role role() {
        return null;
    }
}
