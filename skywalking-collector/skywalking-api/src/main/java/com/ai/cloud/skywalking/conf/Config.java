package com.ai.cloud.skywalking.conf;

public class Config {

    public static class SkyWalking {
        public static String USER_ID = "";

        public static String APPLICATION_CODE = "";

        public static String AUTH_SYSTEM_ENV_NAME = "SKYWALKING_RUN";

        public static boolean AUTH_OVERRIDE = false;

        public static String CHARSET = "UTF-8";

        public static boolean ALL_METHOD_MONITOR = false;

        public static boolean IS_PREMAIN_MODE = false;

        public static String AGENT_BASE_PATH = "";

        public static boolean SELF_DEFINE_METHOD_INTERCEPTOR = false;

        public static String SELF_DEFINE_METHOD_PACKAGE = "";

        public static boolean RECORD_PARAM = false;
    }


    public static class BuriedPoint {
        // 是否打印埋点信息
        public static boolean PRINTF = false;

        public static int MAX_EXCEPTION_STACK_LENGTH = 4000;

        // Business Key 最大长度
        public static int BUSINESSKEY_MAX_LENGTH = 300;

        // 使用逗号分离
        public static String EXCLUSIVE_EXCEPTIONS = "";
    }


    public static class Consumer {
        // 最大消费线程数
        public static int  MAX_CONSUMER  = 2;
        // 消费者最大等待时间
        public static long MAX_WAIT_TIME = 5L;

        //
        public static long CONSUMER_FAIL_RETRY_WAIT_INTERVAL = 50L;
    }


    public static class Buffer {
        // 每个Buffer的最大个数
        public static int BUFFER_MAX_SIZE = 20000;

        // Buffer池的最大长度
        public static int POOL_SIZE = 5;
    }


    public static class Sender {
        // 最大发送数据个数
        public static final int MAX_SEND_DATA_SIZE = 10;
        // 最大发送者的连接数阀比例
        public static       int CONNECT_PERCENT    = 50;

        // 发送服务端配置
        public static String SERVERS_ADDR = "127.0.0.1:34000";

        // 最大发送副本数量
        public static int MAX_COPY_NUM = 2;

        // 发送的最大长度
        public static int MAX_SEND_LENGTH = 18500;

        public static long RETRY_GET_SENDER_WAIT_INTERVAL = 2000L;

        // 切换Sender的周期
        public static long SWITCH_SENDER_INTERVAL = 10 * 60 * 1000;

        // 切换Sender之后，关闭Sender的倒计时
        public static long CLOSE_SENDER_COUNTDOWN = 10 * 1000;

        // Checker线程处理完成等待周期
        public static long CHECKER_THREAD_WAIT_INTERVAL = 1000;

        public static long RETRY_FIND_CONNECTION_SENDER = 1000;
    }


    public static class HealthCollector {
        // 默认健康检查上报时间
        public static long REPORT_INTERVAL = 5 * 60 * 1000L;
    }


    public static class Logging {
        // log文件名
        public static String LOG_FILE_NAME              = "skywalking-api.log";
        // log文件文件夹名字
        public static String LOG_DIR_NAME               = "logs";
        // 最大文件大小
        public static int    MAX_LOG_FILE_LENGTH        = 300 * 1024 * 1024;
        // skywalking 系统错误文件日志
        public static String SYSTEM_ERROR_LOG_FILE_NAME = "skywalking-api-error.log";
    }
}
