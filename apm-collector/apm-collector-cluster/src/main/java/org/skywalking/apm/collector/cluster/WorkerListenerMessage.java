package org.skywalking.apm.collector.cluster;

import java.io.Serializable;
import org.skywalking.apm.collector.actor.AbstractWorker;
import org.skywalking.apm.collector.actor.Role;

/**
 * <code>WorkerListenerMessage</code> is a message just for the worker
 * implementation of the {@link AbstractWorker}
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
