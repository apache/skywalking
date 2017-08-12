package org.skywalking.apm.collector.ui.dao;

import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class ApplicationH2DAO extends H2DAO implements IApplicationDAO {

    @Override public String getApplicationCode(int applicationId) {
        return null;
    }
}
