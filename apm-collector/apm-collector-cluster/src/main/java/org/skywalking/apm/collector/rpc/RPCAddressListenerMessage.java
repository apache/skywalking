package org.skywalking.apm.collector.rpc;

import java.io.Serializable;

/**
 * @author pengys5
 */
public class RPCAddressListenerMessage {

    public static class ConfigMessage implements Serializable {
        private final RPCAddress config;

        public ConfigMessage(RPCAddress config) {
            this.config = config;
        }

        public RPCAddress getConfig() {
            return config;
        }
    }
}
