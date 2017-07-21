package org.skywalking.apm.collector.storage.h2.define;

import java.util.List;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.storage.StorageException;
import org.skywalking.apm.collector.core.storage.StorageInstallException;
import org.skywalking.apm.collector.core.storage.StorageInstaller;
import org.skywalking.apm.collector.core.storage.TableDefine;

/**
 * @author pengys5
 */
public class H2StorageInstaller extends StorageInstaller {

    @Override protected void defineFilter(List<TableDefine> tableDefines) {
        int size = tableDefines.size();
        for (int i = size - 1; i >= 0; i--) {
            if (!(tableDefines.get(i) instanceof H2TableDefine)) {
                tableDefines.remove(i);
            }
        }
    }

    @Override protected boolean isExists(Client client, TableDefine tableDefine) throws StorageException {
        return false;
    }

    @Override protected boolean deleteIndex(Client client, TableDefine tableDefine) throws StorageException {
        return false;
    }

    @Override protected boolean createTable(Client client, TableDefine tableDefine) throws StorageException {
        H2Client h2Client = (H2Client)client;
        H2TableDefine h2TableDefine = (H2TableDefine)tableDefine;

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE TABLE ").append(h2TableDefine.getName()).append(" (");

        h2TableDefine.getColumnDefines().forEach(columnDefine -> {
            H2ColumnDefine h2ColumnDefine = (H2ColumnDefine)columnDefine;
            if (h2ColumnDefine.getType().equals(H2ColumnDefine.Type.Varchar.name())) {
                sqlBuilder.append(h2ColumnDefine.getName()).append(" ").append(h2ColumnDefine.getType()).append("(255)");
            } else {
                sqlBuilder.append(h2ColumnDefine.getName()).append(" ").append(h2ColumnDefine.getType());
            }
        });

        sqlBuilder.append(")");
        try {
            h2Client.execute(sqlBuilder.toString());
        } catch (H2ClientException e) {
            throw new StorageInstallException(e.getMessage(), e);
        }
        return true;
    }
}
