package org.skywalking.apm.collector.storage.h2.dao;

import org.skywalking.apm.collector.core.framework.DefinitionFile;

/**
 * @author pengys5
 */
public class H2DAODefinitionFile extends DefinitionFile {

    @Override protected String fileName() {
        return "h2_dao.define";
    }
}
