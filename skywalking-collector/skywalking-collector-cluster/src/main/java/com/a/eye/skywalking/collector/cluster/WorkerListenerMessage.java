package com.a.eye.skywalking.collector.cluster;

import java.io.Serializable;

/**
 * <code>WorkerListenerMessage</code> is a message just for the worker
 * implementation of the {@link com.a.eye.skywalking.collector.actor.AbstractWorker}
 * to register.
 *
 * @author pengys5
 */
public class WorkerListenerMessage {

    public static class RegisterMessage implements Serializable {
        public final String workRole;

        public RegisterMessage(String workRole) {
            this.workRole = workRole;
        }

        public String getWorkRole() {
            return workRole;
        }
    }
}
