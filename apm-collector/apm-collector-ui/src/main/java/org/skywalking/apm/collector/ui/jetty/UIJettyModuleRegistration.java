package org.skywalking.apm.collector.ui.jetty;

import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public class UIJettyModuleRegistration extends ModuleRegistration {

    @Override public Value buildValue() {
        return new Value(UIJettyConfig.HOST, UIJettyConfig.PORT, UIJettyConfig.CONTEXT_PATH);
    }
}
