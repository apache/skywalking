package com.a.eye.skywalking.collector.cluster;

import java.io.Serializable;

/**
 * @author pengys5
 */
public class WorkerListenerMessage {

    public static class RegisterMessage implements Serializable {
        public final String role;

        public RegisterMessage(String role) {
            this.role = role;
        }

        public String getRole() {
            return role;
        }
    }
}
