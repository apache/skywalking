package com.a.eye.skywalking.storage.config;

/**
 * Created by xin on 2016/11/2.
 */
public class Config {
    public static class Server {
        public static int PORT = 34000;
    }


    public static class BlockIndex {

        public static String STORAGE_BASE_PATH = "/tmp/skywalking/block_index";

        public static String DATA_FILE_INDEX_FILE_NAME = "data_file.index";
    }


    public static class DataFile {
        public static String BASE_PATH = "/tmp/skywalking/data/file";

        public static long MAX_LENGTH = 3 * 1024 * 1024 * 1024;
    }


    public static class DataIndex {

        public static String TABLE_NAME = "data_index";

        public static String BASE_PATH = "/tmp/skywalking/data/index";

        public static String STORAGE_INDEX_FILE_NAME = "dataIndex";

        public static long MAX_CAPACITY_PER_INDEX = 1000 * 1000 * 1000 * 1000;

    }


    public static class RegistryCenter {

        public static String AUTH_INFO = "";

        public static String AUTH_SCHEMA = "";

        public static String CONNECT_URL = "127.0.0.1:2181";

        public static String REGISTRY_PATH_PREFIX = "/storage_list/";
    }


    public static class SpanFinder {
        public static int MAX_CACHE_SIZE = 10;
    }
}
