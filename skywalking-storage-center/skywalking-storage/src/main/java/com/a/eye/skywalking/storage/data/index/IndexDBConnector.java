package com.a.eye.skywalking.storage.data.index;

import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.data.exception.ConnectorInitializeFailedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;

import static com.a.eye.skywalking.storage.config.Constants.SQL.*;

/**
 * Created by xin on 2016/11/4.
 */
public class IndexDBConnector {

    private static final int MAX_BATCH_SIZE = 20;

    private static Logger logger = LogManager.getLogger(IndexDBConnector.class);

    static {
        try {
            Class.forName("org.hsqldb.jdbc.JDBCDriver");
        } catch (ClassNotFoundException e) {
            //never
        }
    }

    private long       timestamp;
    private Connection connection;
    private ConnectURLGenerator generator =
            new ConnectURLGenerator(Config.DataIndex.BASE_PATH, Config.DataIndex.STORAGE_INDEX_FILE_NAME);

    public IndexDBConnector(long timestamp) {
        this.timestamp = timestamp;
        createConnection();
        createTableAndIndexIfNecessary();
    }

    private void createTableAndIndexIfNecessary() {
        try {
            if (validateTableIsExists()) {
                createTable();
                createIndex();
            }
        } catch (SQLException e) {
            throw new ConnectorInitializeFailedException("Failed to create table and index.", e);
        }
    }


    private void createConnection() {
        try {
            connection = DriverManager.getConnection(generator.generate(timestamp));
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            throw new ConnectorInitializeFailedException("Failed to create connection.", e);
        }
    }

    private boolean validateTableIsExists() throws SQLException {
        PreparedStatement ps = connection.prepareStatement(QUERY_TABLES);
        ResultSet rs = ps.executeQuery();
        rs.next();

        boolean exists = rs.getInt("TABLE_COUNT") == 1;
        rs.close();
        ps.close();

        return exists;
    }

    private void createTable() throws SQLException {
        PreparedStatement ps = connection.prepareStatement(CREATE_TABLE);
        ps.execute();
        ps.close();
    }

    private void createIndex() throws SQLException {
        PreparedStatement ps = connection.prepareStatement(CREATE_INDEX);
        ps.execute();
        ps.close();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void batchUpdate(IndexMetaGroup<Long> metaGroup) throws SQLException {
        int currentIndex = 0;
        PreparedStatement ps = connection.prepareStatement(INSERT_INDEX);
        for (IndexMetaInfo metaInfo : metaGroup.getMetaInfo()) {
            ps.setString(1, metaInfo.getTraceId());
            ps.setString(2, metaInfo.getParentLevelId());
            ps.setInt(3, metaInfo.getLevelId());
            ps.setString(4, metaInfo.getFileName());
            ps.setLong(5, metaInfo.getOffset());
            ps.setInt(6, metaInfo.getLength());
            ps.addBatch();
            if (++currentIndex > MAX_BATCH_SIZE) {
                ps.executeBatch();
            }
        }
        ps.executeBatch();
        ps.close();
    }

    public long fetchIndexSize() throws SQLException {
        PreparedStatement ps = connection.prepareStatement(QUERY_INDEX_SIZE);
        ResultSet rs = ps.executeQuery();
        rs.next();

        long indexSize = rs.getLong("INDEX_SIZE");
        rs.close();
        ps.close();

        return indexSize;
    }

    public IndexMetaCollection queryByTraceId(String traceId) {
        return null;
    }

    class ConnectURLGenerator {

        private String basePath;
        private String dbFileName;

        private ConnectURLGenerator(String basePath, String dbFileName) {
            this.basePath = basePath;
            this.dbFileName = dbFileName;
        }


        public String generate(long timestamp) {
            return "jdbc:hsqldb:file:" + basePath + "/" + timestamp + "/" + dbFileName;
        }
    }

    public void close(){
        //TODO:
    }
}
