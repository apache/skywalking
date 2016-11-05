package com.a.eye.skywalking.storage.config;

/**
 * Created by xin on 2016/11/2.
 */
public class Config {
    public static class BlockIndex {

        public static String STORAGE_BASE_PATH = "/tmp/skywalking/index";

        public static String DATA_FILE_INDEX_FILE_NAME = "data_file.index";
    }


    public static class DataFile {
        public static String BASE_PATH = "";

        public static long MAX_LENGTH = 3 * 1024 * 1024 * 1024;
    }


    public static class DataIndex {

        public static String TABLE_NAME = "data_index";

        public static String BASE_PATH = "";

        public static String STORAGE_INDEX_FILE_NAME = "";
    }
}
