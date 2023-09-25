package org.apache.skywalking.oap.server.receiver.envoy.graal;

import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverProvider;

/**
 * In the old logic, class (@link org.apache.skywalking.oap.server.receiver.envoy.als.mxFieldsHelper) was used, which is not supported by native-image,
 * so we change the execution of `prepare()` to the compilation stage, see (@link org.apache.skywalking.graal.Generator).
 */
public class EnvoyMetricReceiverProviderGraal extends EnvoyMetricReceiverProvider {
    @Override
    public String name() {
        return "default-graalvm";
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {

    }
}
