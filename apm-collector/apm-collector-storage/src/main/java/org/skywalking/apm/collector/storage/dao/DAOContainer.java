package org.skywalking.apm.collector.storage.dao;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public enum DAOContainer {
    INSTANCE;

    private Map<String, DAO> daos = new HashMap<>();

    public void put(String interfaceName, DAO dao) {
        daos.put(interfaceName, dao);
    }

    public DAO get(String interfaceName) {
        return daos.get(interfaceName);
    }
}
