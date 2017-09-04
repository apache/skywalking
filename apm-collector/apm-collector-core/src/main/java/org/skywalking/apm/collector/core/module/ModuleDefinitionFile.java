package org.skywalking.apm.collector.core.module;

import org.skywalking.apm.collector.core.framework.DefinitionFile;

/**
 * @author pengys5
 */
public class ModuleDefinitionFile extends DefinitionFile {
    @Override protected String fileName() {
        return "module.define";
    }
}
