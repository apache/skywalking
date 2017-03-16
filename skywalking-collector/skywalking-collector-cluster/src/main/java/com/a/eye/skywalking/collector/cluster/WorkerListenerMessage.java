package com.a.eye.skywalking.collector.cluster;

import com.a.eye.skywalking.collector.actor.Role;

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
        private final Role role;

        public RegisterMessage(Role role) {
            this.role = role;
        }

        public Role getRole() {
            return role;
        }
    }
}
