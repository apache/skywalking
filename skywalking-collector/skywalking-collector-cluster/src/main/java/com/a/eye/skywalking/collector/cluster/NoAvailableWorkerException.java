package com.a.eye.skywalking.collector.cluster;

/**
 * The <code>NoAvailableWorkerException</code> represents no available member，
 * when a {@link WorkerSelector} try to select.
 *
 * Most likely, in the cluster, these is no active worker of the particular role.
 *
 * @author wusheng
 */
public class NoAvailableWorkerException extends Exception {
    public NoAvailableWorkerException(String message){
        super(message);
    }
}
