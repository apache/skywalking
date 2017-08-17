package org.skywalking.apm.collector.core.storage;

import org.skywalking.apm.collector.core.framework.DefinitionFile;

/**
 * @author pengys5
 */
public class StorageDefinitionFile extends DefinitionFile {
    @Override protected String fileName() {
        return "storage.define";
    }
}
