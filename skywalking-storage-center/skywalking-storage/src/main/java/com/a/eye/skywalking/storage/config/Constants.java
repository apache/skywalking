package com.a.eye.skywalking.storage.config;

public class Constants {

    public final static String TABLE_NAME        = "data_index";
    public static final String DRIVER_CLASS_NAME = "org.hsqldb.jdbc.JDBCDriver";

    public static int MAX_BATCH_SIZE = 50;

    public static class SQL {
        public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "\n" + "(\n"
                + "    id INT PRIMARY KEY NOT NULL IDENTITY,\n"
                + "    tid_s0 INT NOT NULL,\n"
                + "    tid_s1 BIGINT NOT NULL,\n"
                + "    tid_s2 INT NOT NULL,\n"
                + "    tid_s3 INT NOT NULL,\n"
                + "    tid_s4 INT NOT NULL,\n"
                + "    tid_s5 INT NOT NULL,\n"
                + "    span_type INT NOT NULL, \n"
                + "    file_name BIGINT NOT NULL,\n"
                + "    file_name_suffix INT NOT NULL,\n"
                + "    offset BIGINT NOT NULL,\n"
                + "    length INT NOT NULL\n" + ");\n";

        public static final String CREATE_INDEX = "CREATE INDEX \"index_data_trace_id_index\" ON " + TABLE_NAME + " "
                + "(tid_s0,tid_s1,tid_s2,tid_s3,tid_s4,tid_s5);";

        public static final String INSERT_INDEX = "INSERT INTO " +TABLE_NAME + "(tid_s0,tid_s1,tid_s2,tid_s3,tid_s4,tid_s5,span_type"
                + ",file_name,file_name_suffix,offset,length) VALUES(?,?,?,?,?,?,?,?,?,?,?)";

        public static final String QUERY_TABLES = "SELECT count(1) AS TABLE_COUNT FROM   INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_NAME= '" + TABLE_NAME.toUpperCase() + "';";

        public static final String QUERY_INDEX_SIZE = "SELECT count(1) AS INDEX_SIZE FROM " + TABLE_NAME;

        public static final String QUERY_TRACE_ID = "SELECT span_type, file_name,file_name_suffix, offset, length "
                + " FROM "+ TABLE_NAME+ " WHERE tid_s0 = ? AND tid_s1 = ? AND tid_s2 = ? AND tid_s3=? AND tid_s4=? AND"
                + " tid_s5 = ?";


        public static final String DEFAULT_USER = "root";

        public static final String DEFAULT_PASSWORD = "root";
    }

}
