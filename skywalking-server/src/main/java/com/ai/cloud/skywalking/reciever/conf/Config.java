package com.ai.cloud.skywalking.reciever.conf;

public class Config {

    // 采集服务配置类
    public static class Server {
        // 采集服务器的端口
        public static int PORT = 34000;
    }

    // 数据缓存配置类
    public static class Buffer {
        // 最大数据缓存线程数量
        public static int MAX_THREAD_NUMBER = 3;

        //每个线程最大缓存数量
        public static int PER_THREAD_MAX_BUFFER_NUMBER = 1024;

        // 无数据处理时轮询等待时间(单位:毫秒)
        public static long MAX_WAIT_TIME = 5000L;

        // 数据冲突时等待时间(单位:毫秒)
        public static long DATA_CONFLICT_WAIT_TIME = 10L;

        // 数据缓存文件目录
        public static String DATA_BUFFER_FILE_PARENT_DIRECTORY = "../data/buffer";

        // 缓存数据文件最大长度(单位:byte)
        public static int DATA_FILE_MAX_LENGTH = 30 * 1024 * 1024;

        // 每次缓存数据写入失败，最大尝试时间
        public static long WRITE_DATA_FAILURE_RETRY_INTERVAL = 10 * 60 * 1000L;

        //每次Flush的缓存数据的个数
        public static int FLUSH_NUMBER_OF_CACHE = 30;

    }

    public static class Persistence {
        // 最大持久化的线程数量
        public static int MAX_THREAD_NUMBER = 1;

        // 定位文件时,每次读取偏移量跳过大小
        public static int OFFSET_FILE_SKIP_LENGTH = 2048;

        // 每次读取文件偏移量大小

        // 处理文件完成之后，等待时间
        public static long SWITCH_FILE_WAIT_TIME = 5000L;

        // 追加EOF标志位的线程数量
        public static int MAX_APPEND_EOF_FLAGS_THREAD_NUMBER = 2;

        // 每次存储的最大数量
        public static final int MAX_STORAGE_SIZE_PER_TIME = 1024 * 1024;
    }

    public static class RegisterPersistence {
        // 偏移量注册文件的目录
        public static String REGISTER_FILE_PARENT_DIRECTORY = "../data/offset";

        // 偏移量注册文件名
        public static String REGISTER_FILE_NAME = "offset.txt";

        // 偏移量注册备份文件名
        public static String REGISTER_BAK_FILE_NAME = "offset.txt.bak";

        // 偏移量写入文件等待周期
        public static long OFFSET_WRITTEN_FILE_WAIT_CYCLE = 5000L;
    }

    public static class HBaseConfig {
        //
        public static String TABLE_NAME = "sw-call-chain";
        //
        public static String FAMILY_COLUMN_NAME = "call-chain";

        public static String ZK_HOSTNAME;

        public static String CLIENT_PORT;
    }

    public static class StorageChain {
        public static long RETRY_STORAGE_WAIT_TIME = 50L;
        
        public static String STORAGE_TYPE = "hbase";
    }
}