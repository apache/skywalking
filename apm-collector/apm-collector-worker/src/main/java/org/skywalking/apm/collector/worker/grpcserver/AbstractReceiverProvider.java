package org.skywalking.apm.collector.worker.grpcserver;

import org.skywalking.apm.collector.actor.AbstractLocalSyncWorker;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;

/**
 * @author pengys5
 */
public abstract class AbstractReceiverProvider<T extends AbstractLocalSyncWorker> extends AbstractLocalSyncWorkerProvider<T> {

}
