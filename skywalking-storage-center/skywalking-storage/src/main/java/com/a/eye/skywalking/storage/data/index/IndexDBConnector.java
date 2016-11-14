package com.a.eye.skywalking.storage.data.index;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.config.Constants;
import com.a.eye.skywalking.storage.data.exception.ConnectorInitializeFailedException;
import com.a.eye.skywalking.storage.data.spandata.AckSpanData;
import com.a.eye.skywalking.storage.data.spandata.RequestSpanData;
import com.a.eye.skywalking.storage.data.spandata.SpanData;
import com.a.eye.skywalking.storage.data.spandata.SpanType;

import java.sql.*;

import static com.a.eye.skywalking.storage.config.Constants.SQL.*;
import static com.a.eye.skywalking.storage.util.PathResolver.getAbsolutePath;

/**
 * Created by xin on 2016/11/4.
 */
public class IndexDBConnector {

    private static ILog logger = LogManager.getLogger(IndexDBConnector.class);

    static {
        try {
            Class.forName(Constants.DRIVER_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            //never
        }
    }

    private long       timestamp;
    private Connection connection;
    private ConnectURLGenerator generator =
            new ConnectURLGenerator(getAbsolutePath(Config.DataIndex.PATH), Config.DataIndex.FILE_NAME);

    public IndexDBConnector(long timestamp) {
        this.timestamp = timestamp;
        createConnection();
        createTableAndIndexIfNecessary();
    }

    public IndexDBConnector(Connection connection) {
        this.connection = connection;
        createTableAndIndexIfNecessary();
    }

    private void createTableAndIndexIfNecessary() {
        try {
            if (!tableExists()) {
                createTable();
                createIndex();
            }
        } catch (SQLException e) {
            throw new ConnectorInitializeFailedException("Failed to create table and index.", e);
        }
    }


    private void createConnection() {
        try {
            connection = DriverManager.getConnection(generator.generate(timestamp), DEFAULT_USER, DEFAULT_PASSWORD);
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            throw new ConnectorInitializeFailedException("Failed to create connection.", e);
        }
    }

    private boolean tableExists() throws SQLException {
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
            ps.setInt(2, metaInfo.getSpanType().getValue());
            ps.setString(3, metaInfo.getFileName());
            ps.setLong(4, metaInfo.getOffset());
            ps.setInt(5, metaInfo.getLength());
            ps.addBatch();
            if (++currentIndex > Constants.MAX_BATCH_SIZE) {
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
        try {
            PreparedStatement ps = connection.prepareStatement(QUERY_TRACE_ID);
            ps.setString(1, traceId);
            ResultSet rs = ps.executeQuery();

            IndexMetaCollection collection = new IndexMetaCollection();
            while (rs.next()) {
                SpanType spanType = SpanType.convert(rs.getInt("span_type"));
                SpanData spanData = null;

                if (SpanType.ACKSpan == spanType) {
                    spanData = new AckSpanData();
                } else if (SpanType.RequestSpan == spanType) {
                    spanData = new RequestSpanData();
                }

                collection.add(new IndexMetaInfo(spanData, rs.getString("file_name"), rs.getLong("offset"),
                        rs.getInt("length")));
            }
            return collection;
        } catch (SQLException e) {
            logger.error("Failed to query trace Id [{}]", traceId, e);
            return new IndexMetaCollection();
        }
    }

    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            logger.error("Failed to close index db connector", e);
        }
    }
}
