package com.a.eye.skywalking.reciever.conf;

public class Config {

    // 采集服务配置类
    public static class Server {
        // 采集服务器的端口
        public static int PORT                                = 34000;

        // 异常数据的时间间隔
        public static int FAILED_PACKAGE_WATCHING_TIME_WINDOW = 5 * 60;
        // 时间间隔内最大异常数据次数
        public static int MAX_WATCHING_FAILED_PACKAGE_SIZE    = 200;
    }


    // 数据缓存配置类
    public static class Buffer {
        // 数据缓存文件目录
        public static String DATA_BUFFER_FILE_PARENT_DIR = "/tmp/skywalking/data/buffer";

        //每个线程最大缓存数量
        public static int PER_THREAD_MAX_BUFFER_NUMBER = 1024;

        // 无数据处理时轮询等待时间(单位:毫秒)
        public static long MAX_WAIT_TIME = 5000L;

        // 数据冲突时等待时间(单位:毫秒)
        public static long DATA_CONFLICT_WAIT_TIME = 10L;

        public static long BUFFER_FILE_MAX_LENGTH = 30 * 1024 * 1024;

        public static int  BUFFER_DEAL_THREAD_NUMBER = 0;

    }


    public static class RegisterPersistence {
        // 偏移量注册文件的目录
        public static String REGISTER_FILE_PARENT_DIRECTORY = "../data/offset";

        // 偏移量注册文件名
        public static String REGISTER_FILE_NAME = "offset.A";

        // 偏移量注册备份文件名
        public static String REGISTER_BAK_FILE_NAME = "offset.B";

        // 偏移量写入文件等待周期
        public static long OFFSET_WRITTEN_FILE_WAIT_CYCLE = 5000L;
    }


    public static class Persistence {
        // 最大数据处理线程数量
        public static int MAX_DEAL_DATA_THREAD_NUMBER         = 3;

        // 切换文件，等待时间
        public static long SWITCH_FILE_WAIT_TIME = 5000L;

        // 追加EOF标志位的线程数量
        public static int MAX_APPEND_EOF_FLAGS_THREAD_NUMBER = 1;

    }

    public static class Alarm {

        public static int ALARM_EXPIRE_SECONDS = 1000 * 60 * 90;

        public static int ALARM_EXCEPTION_STACK_LENGTH = 300;

        public static String REDIS_SERVER = "127.0.0.1:6379";

        public static int REDIS_MAX_IDLE = 10;

        public static int REDIS_MIN_IDLE = 1;

        public static int REDIS_MAX_TOTAL = 20;

        public static boolean ALARM_OFF_FLAG = false;

        public static long ALARM_REDIS_INSPECTOR_INTERVAL = 5 * 1000L;

        public static class Checker {
            public static boolean TURN_ON_EXCEPTION_CHECKER = true;

            public static boolean TURN_ON_EXECUTE_TIME_CHECKER = true;
        }
    }

    public static class HBaseConfig {

        public static String TABLE_NAME = "sw-call-chain";

        public static String FAMILY_COLUMN_NAME = "call-chain";

        public static String ZK_HOSTNAME;

        public static String CLIENT_PORT;
    }

    public static class Redis {

        public static String REDIS_SERVER = "10.1.241.18:16379";

        public static int REDIS_MAX_IDLE = 10;

        public static int REDIS_MIN_IDLE = 1;

        public static int REDIS_MAX_TOTAL = 20;
    }

    public static class HealthCollector {
        // 默认健康检查上报时间
        public static long REPORT_INTERVAL = 5 * 60 * 1000L;
    }

}
