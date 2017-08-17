package org.skywalking.apm.collector.stream.worker;

import org.skywalking.apm.collector.core.framework.DefinitionFile;

/**
 * @author pengys5
 */
public class RemoteWorkerProviderDefinitionFile extends DefinitionFile {
    @Override protected String fileName() {
        return "remote_worker_provider.define";
    }
}
