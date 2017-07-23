package org.skywalking.apm.collector.remote.grpc;

import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public class RemoteGRPCModuleRegistration extends ModuleRegistration {

    @Override public Value buildValue() {
        return new Value(RemoteGRPCConfig.HOST, RemoteGRPCConfig.PORT, null);
    }
}
