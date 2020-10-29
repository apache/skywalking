package org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

/**
 * TiDB storage enhanced and came from MySQLStorageProvider to support TiDB.
 *
 * cause: need add "useAffectedRows=true" to jdbc url.
 */
@Slf4j
public class TiDBStorageProvider extends MySQLStorageProvider {

    @Override
    public String name() {
        return "tidb";
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
        super.prepare();
        this.registerServiceImplementation(IHistoryDeleteDAO.class, new TiDBHistoryDeleteDAO(this.mysqlClient));
    }
}
