package com.a.eye.skywalking.collector.cluster;

/**
 * The <code>NoAvailableWorkerException</code> represents no available member，
 * when the {@link WorkersRefCenter#availableWorks(String)} try to get the list.
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
