package org.skywalking.apm.collector.core.module;

import org.skywalking.apm.collector.core.framework.DefinitionFile;

/**
 * @author pengys5
 */
public class ModuleGroupDefineFile extends DefinitionFile {
    @Override protected String fileName() {
        return "group.define";
    }
}
