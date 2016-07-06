package com.ai.cloud.skywalking.reciever.conf;

public class Config {

    // 采集服务配置类
    public static class Server {
        // 采集服务器的端口
        public static int PORT                                = 34000;
        // 最大数据处理线程数量
        public static int MAX_DEAL_DATA_THREAD_NUMBER         = 3;
        // 异常数据的时间间隔
        public static int FAILED_PACKAGE_WATCHING_TIME_WINDOW = 5 * 60;
        // 时间间隔内最大异常数据次数
        public static int MAX_WATCHING_FAILED_PACKAGE_SIZE    = 200;
    }


    // 数据缓存配置类
    public static class Buffer {

        //每个线程最大缓存数量
        public static int PER_THREAD_MAX_BUFFER_NUMBER = 1024;

        // 无数据处理时轮询等待时间(单位:毫秒)
        public static long MAX_WAIT_TIME = 5000L;

        // 数据冲突时等待时间(单位:毫秒)
        public static long DATA_CONFLICT_WAIT_TIME = 10L;

    }


    public static class HBaseConfig {

        public static String TABLE_NAME = "sw-call-chain";

        public static String FAMILY_COLUMN_NAME = "call-chain";

        public static String ZK_HOSTNAME;

        public static String CLIENT_PORT;
    }


    public static class StorageChain {
        public static String STORAGE_TYPE = "hbase";
    }


    public static class Redis {

        public static String REDIS_SERVER = "10.1.241.18:16379";

        public static int REDIS_MAX_IDLE = 10;

        public static int REDIS_MIN_IDLE = 1;

        public static int REDIS_MAX_TOTAL = 20;
    }


    public static class Alarm {

        public static int ALARM_EXPIRE_SECONDS = 1000 * 60 * 90;

        public static int ALARM_EXCEPTION_STACK_LENGTH = 300;

        public static boolean ALARM_OFF_FLAG = false;

        public static long ALARM_REDIS_INSPECTOR_INTERVAL = 5 * 1000L;


        public static class Checker {
            public static boolean TURN_ON_EXCEPTION_CHECKER = true;

            public static boolean TURN_ON_EXECUTE_TIME_CHECKER = true;
        }
    }


    public static class HealthCollector {
        // 默认健康检查上报时间
        public static long REPORT_INTERVAL = 5 * 60 * 1000L;
    }

}
