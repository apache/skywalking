package org.skywalking.apm.collector.storage.h2.define;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.storage.StorageException;
import org.skywalking.apm.collector.core.storage.StorageInstallException;
import org.skywalking.apm.collector.core.storage.StorageInstaller;
import org.skywalking.apm.collector.core.storage.TableDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author pengys5
 */
public class H2StorageInstaller extends StorageInstaller {

    private final Logger logger = LoggerFactory.getLogger(H2StorageInstaller.class);

    @Override protected void defineFilter(List<TableDefine> tableDefines) {
        int size = tableDefines.size();
        for (int i = size - 1; i >= 0; i--) {
            if (!(tableDefines.get(i) instanceof H2TableDefine)) {
                tableDefines.remove(i);
            }
        }
    }

    @Override protected boolean isExists(Client client, TableDefine tableDefine) throws StorageException {
        H2Client h2Client = (H2Client)client;
        ResultSet rs = null;
        try {
            logger.info("check if table {} exist ", tableDefine.getName());
            rs = h2Client.getConnection().getMetaData().getTables(null, null, tableDefine.getName().toUpperCase(), null);
            if (rs.next()) {
                return true;
            }
        } catch (SQLException | H2ClientException e) {
            throw new StorageInstallException(e.getMessage(), e);
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
            } catch (SQLException e) {
                throw new StorageInstallException(e.getMessage(), e);
            }
        }
        return false;
    }

    @Override protected boolean deleteTable(Client client, TableDefine tableDefine) throws StorageException {
        H2Client h2Client = (H2Client)client;
        try {
            h2Client.execute("drop table if exists " + tableDefine.getName());
            return true;
        } catch (H2ClientException e) {
            throw new StorageInstallException(e.getMessage(), e);
        }
    }

    @Override protected boolean createTable(Client client, TableDefine tableDefine) throws StorageException {
        H2Client h2Client = (H2Client)client;
        H2TableDefine h2TableDefine = (H2TableDefine)tableDefine;

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE TABLE ").append(h2TableDefine.getName()).append(" (");

        h2TableDefine.getColumnDefines().forEach(columnDefine -> {
            H2ColumnDefine h2ColumnDefine = (H2ColumnDefine)columnDefine;
            if (h2ColumnDefine.getType().equals(H2ColumnDefine.Type.Varchar.name())) {
                sqlBuilder.append(h2ColumnDefine.getName()).append(" ").append(h2ColumnDefine.getType()).append("(255),");
            } else {
                sqlBuilder.append(h2ColumnDefine.getName()).append(" ").append(h2ColumnDefine.getType()).append(",");
            }
        });
        //remove last comma
        sqlBuilder.delete(sqlBuilder.length() - 1, sqlBuilder.length());
        sqlBuilder.append(")");
        try {
            logger.info("create h2 table with sql {}", sqlBuilder);
            h2Client.execute(sqlBuilder.toString());
        } catch (H2ClientException e) {
            throw new StorageInstallException(e.getMessage(), e);
        }
        return true;
    }
}
