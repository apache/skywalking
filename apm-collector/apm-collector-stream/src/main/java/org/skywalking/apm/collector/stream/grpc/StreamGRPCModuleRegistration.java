package org.skywalking.apm.collector.stream.grpc;

import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public class StreamGRPCModuleRegistration extends ModuleRegistration {

    @Override public Value buildValue() {
        return new Value(StreamGRPCConfig.HOST, StreamGRPCConfig.PORT, null);
    }
}
