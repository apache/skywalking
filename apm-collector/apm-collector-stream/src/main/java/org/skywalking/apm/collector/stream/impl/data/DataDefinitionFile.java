package org.skywalking.apm.collector.stream.impl.data;

import org.skywalking.apm.collector.core.framework.DefinitionFile;

/**
 * @author pengys5
 */
public class DataDefinitionFile extends DefinitionFile {

    @Override protected String fileName() {
        return "data.define";
    }
}
