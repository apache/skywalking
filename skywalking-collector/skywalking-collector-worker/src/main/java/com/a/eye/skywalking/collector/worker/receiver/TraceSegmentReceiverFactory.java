package com.a.eye.skywalking.collector.worker.receiver;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;
import com.a.eye.skywalking.collector.worker.application.member.ApplicationDiscoverMember;

/**
 * @author pengys5
 */
public class TraceSegmentReceiverFactory extends AbstractWorkerProvider {
    @Override
    public Class workerClass() {
        return ApplicationDiscoverMember.class;
    }

    @Override
    public int workerNum() {
        return 0;
    }
}
