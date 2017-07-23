package org.skywalking.apm.collector.remote.grpc.handler;

import org.skywalking.apm.collector.core.framework.DefinitionFile;

/**
 * @author pengys5
 */
public class RemoteHandlerDefinitionFile extends DefinitionFile {

    @Override protected String fileName() {
        return "remote_handler.define";
    }
}
