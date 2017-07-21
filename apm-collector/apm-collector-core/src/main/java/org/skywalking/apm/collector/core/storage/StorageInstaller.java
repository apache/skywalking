package org.skywalking.apm.collector.core.storage;

import java.util.List;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.config.ConfigException;

/**
 * @author pengys5
 */
public abstract class StorageInstaller {

    public final void install(Client client) throws StorageException {
        StorageDefineLoader defineLoader = new StorageDefineLoader();
        try {
            List<TableDefine> tableDefines = defineLoader.load();
            defineFilter(tableDefines);

            for (TableDefine tableDefine : tableDefines) {
                if (isExists(client, tableDefine)) {
                    deleteIndex(client, tableDefine);
                } else {
                    createTable(client, tableDefine);
                }
            }
        } catch (ConfigException e) {
            throw new StorageInstallException(e.getMessage(), e);
        }
    }

    protected abstract void defineFilter(List<TableDefine> tableDefines);

    protected abstract boolean isExists(Client client, TableDefine tableDefine) throws StorageException;

    protected abstract boolean deleteIndex(Client client, TableDefine tableDefine) throws StorageException;

    protected abstract boolean createTable(Client client, TableDefine tableDefine) throws StorageException;
}
