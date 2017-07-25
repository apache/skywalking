package org.skywalking.apm.collector.storage.elasticsearch.dao;

import org.skywalking.apm.collector.core.framework.DefinitionFile;

/**
 * @author pengys5
 */
public class EsDAODefinitionFile extends DefinitionFile {

    @Override protected String fileName() {
        return "es_dao.define";
    }
}
