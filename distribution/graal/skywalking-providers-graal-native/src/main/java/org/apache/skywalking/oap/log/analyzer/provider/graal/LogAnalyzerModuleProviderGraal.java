package org.apache.skywalking.oap.log.analyzer.provider.graal;

import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

/**
 * The old logic of this class used Groovy;
 * however, GraalVM does not support it well, so it has been replaced with an empty implementation.
 * In the future, other methods will be used to implement this class.
 */
public class LogAnalyzerModuleProviderGraal extends LogAnalyzerModuleProvider {
    @Override
    public String name() {
        return "default-graalvm";
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {

    }
}
