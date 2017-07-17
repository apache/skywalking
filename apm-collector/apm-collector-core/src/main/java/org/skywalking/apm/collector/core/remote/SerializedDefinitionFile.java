package org.skywalking.apm.collector.core.remote;

import org.skywalking.apm.collector.core.framework.DefinitionFile;

/**
 * @author pengys5
 */
public class SerializedDefinitionFile extends DefinitionFile {
    @Override protected String fileName() {
        return "serialized.define";
    }
}
