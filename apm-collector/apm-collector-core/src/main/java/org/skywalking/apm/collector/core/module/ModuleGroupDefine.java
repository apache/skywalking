package org.skywalking.apm.collector.core.module;

import org.skywalking.apm.collector.core.framework.Context;

/**
 * @author pengys5
 */
public interface ModuleGroupDefine {
    String name();

    Context groupContext();

    ModuleInstaller moduleInstaller();
}
