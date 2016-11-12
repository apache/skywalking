package com.a.eye.skywalking.storage.config;

import static com.a.eye.skywalking.storage.config.Config.DataIndex.TABLE_NAME;

public class Constants {

    public static class SQL {
        public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "\n" + "(\n"
                + "    id INT PRIMARY KEY NOT NULL IDENTITY,\n"
                + "    trace_id VARCHAR(32) NOT NULL,\n"
                + "    levelId VARCHAR(1024) NOT NULL,\n"
                + "    span_type INT NOT NULL, \n"
                + "    file_name VARCHAR(10) NOT NULL,\n"
                + "    offset BIGINT NOT NULL,\n"
                + "    length INT NOT NULL\n" + ");\n";

        public static final String CREATE_INDEX = "CREATE INDEX \"index_data_trace_id_index\" ON " + TABLE_NAME + " (trace_id);";

        public static final String INSERT_INDEX = "INSERT INTO " +TABLE_NAME + "(trace_id,levelId,span_type"
                + "file_name,offset,length) VALUES(?,?,?,?,?,?)";

        public static final String QUERY_TABLES = "SELECT count(1) AS TABLE_COUNT FROM   INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_NAME= '" + TABLE_NAME + "';";

        public static final String QUERY_INDEX_SIZE = "SELECT count(1) AS INDEX_SIZE FROM " + TABLE_NAME;

        public static final String QUERY_TRACE_ID = "SELECT span_type, file_name, offset, length "
                + " FROM "+ TABLE_NAME+ " WHERE trace_id = ?";
    }

}
