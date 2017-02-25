package com.a.eye.skywalking.collector.cluster;

import java.io.Serializable;

/**
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
