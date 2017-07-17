package org.skywalking.apm.collector.core.cluster;

import org.skywalking.apm.collector.core.framework.DefinitionFile;

/**
 * @author pengys5
 */
public class ClusterDefinitionFile extends DefinitionFile {

    @Override protected String fileName() {
        return "cluster-configuration.define";
    }
}
