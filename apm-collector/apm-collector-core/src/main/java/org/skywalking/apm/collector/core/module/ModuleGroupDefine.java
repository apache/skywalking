package org.skywalking.apm.collector.core.module;

/**
 * @author pengys5
 */
public interface ModuleGroupDefine {
    String name();

    ModuleInstallMode mode();
}
