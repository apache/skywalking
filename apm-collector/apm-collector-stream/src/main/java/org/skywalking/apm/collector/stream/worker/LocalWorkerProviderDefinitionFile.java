package org.skywalking.apm.collector.stream.worker;

import org.skywalking.apm.collector.core.framework.DefinitionFile;

/**
 * @author pengys5
 */
public class LocalWorkerProviderDefinitionFile extends DefinitionFile {
    @Override protected String fileName() {
        return "local_worker_provider.define";
    }
}
