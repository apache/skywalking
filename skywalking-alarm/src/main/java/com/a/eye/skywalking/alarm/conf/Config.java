package com.a.eye.skywalking.alarm.conf;

public class Config {

    public static class Server {

        public static int PROCESS_THREAD_SIZE = 1;

        public static long DAEMON_THREAD_WAIT_INTERVAL = 50000L;

        public static long ALARM_RULE_ACTIVITY_INTERVAL = 3600000L;

    }

    public static class ProcessThread {
        public static long THREAD_WAIT_INTERVAL = 60 * 1000L;
    }

    public static class ZKPath {

        public static String CONNECT_STR = "127.0.0.1:2181";

        public static int CONNECT_TIMEOUT = 1000;

        public static int RETRY_TIMEOUT = 1000;

        public static int RETRY_TIMES = 3;

        public static String REGISTER_SERVER_PATH = "/skywalking/alarm-server/register-servers";

        public static String COORDINATOR_PATH = "/skywalking/alarm-server/coordinator/lock";

        public static String INSPECTOR_LOCK_PATH = "/skywalking/alarm-server/user-inspector/lock";
    }


    public static class Coordinator {
        // 单位:(秒)
        public static long RETRY_GET_COORDINATOR_LOCK_INTERVAL = 5;

        public static long RETRY_BECOME_COORDINATOR_WAIT_TIME = 10 * 1000L;
        // 单位:(毫秒)
        public static long CHECK_REDISTRIBUTE_INTERVAL = 5 * 1000;
        // 单位:(毫秒)
        public static long CHECK_ALL_PROCESS_THREAD_INTERVAL = 500L;
    }

    public static class DB {

        public static String PASSWORD = "root";

        public static String USER_NAME = "root";

        public static String DRIVER_CLASS = "com.mysql.jdbc.Driver";

        public static String URL = "jdbc:mysql://127.0.0.1:3307/test";

        public static int MAX_IDLE = 1;

        public static int MAX_POOL_SIZE = 20;

        public static long CONNECT_TIMEOUT = 10000;

    }

    public static class InspectThread {
        public static long RETRY_GET_INSPECT_LOCK_INTERVAL = 10000;

        public static long CHECK_USER_LIST_INTERVAL = 5 * 60 * 1000;
    }


    public static class Alarm {

        public static String REDIS_SERVER = "127.0.0.1:6379";

        public static int REDIS_MAX_IDLE = 10;

        public static int REDIS_MIN_IDLE = 1;

        public static int REDIS_MAX_TOTAL = 20;

        public static boolean ALARM_OFF_FLAG = false;

    }

    public static class MailSenderInfo {

        public static String MAIL_HOST = "mail.qq.com";

        public static String TRANSPORT_PROTOCOL = "smtp";

        public static boolean SMTP_AUTH = true;

        public static boolean SSL_ENABLE = false;

        public static String USERNAME = "skywalking@foxmail.com";

        public static String PASSWORD = "tcaeuhuslkjlebjg";

        public static String SENDER = "skywalking@foxmail.com";

    }

    public static class TemplateInfo {
        public static String PORTAL_URL = "http://127.0.0.1:8080/skywalking";
    }
}
