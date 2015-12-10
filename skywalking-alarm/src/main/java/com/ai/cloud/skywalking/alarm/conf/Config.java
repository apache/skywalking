package com.ai.cloud.skywalking.alarm.conf;

public class Config {

    public static class Server {

        public static int PROCESS_THREAD_SIZE = 2;

        public static long DAEMON_THREAD_WAIT_INTERVAL = 50000L;

    }

    public static class ProcessThread {
        public static long THREAD_WAIT_INTERVAL = 60 * 1000L;
    }

    public static class ZKPath {

        public static String CONNECT_STR = "127.0.0.1:2181";

        public static int CONNECT_TIMEOUT = 1000;

        public static int RETRY_TIMEOUT = 1000;

        public static int RETRY_TIMES = 3;

        public static String NODE_PREFIX = "/skywalking";

        public static String COORDINATOR_STATUS_PATH = "/alarm-server/coordinator/status";

        public static String REGISTER_SERVER_PATH = "/alarm-server/register-servers";

        public static String USER_REGISTER_LOCK_PATH = "/alarm-server/users";
    }

    public static class DB {

        public static String PASSWORD = "devrdbusr13";

        public static String USER_NAME = "devrdbusr13";

        public static String DRIVER_CLASS = "com.mysql.jdbc.Driver";

        public static String URL = "jdbc:mysql://10.1.228.200:31306/test";

    }

    public static class Alarm {

        public static String REDIS_SERVER = "127.0.0.1:6379";

        public static int REDIS_MAX_IDLE = 10;

        public static int REDIS_MIN_IDLE = 1;

        public static int REDIS_MAX_TOTAL = 20;

        public static boolean ALARM_OFF_FLAG = false;
    }

    public static class MailSenderInfo {
        public static String configId = "1000";
    }
}