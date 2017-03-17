package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public interface Provider {

    WorkerRef create(ClusterWorkerContext clusterContext, LocalWorkerContext localContext) throws Exception;
}
