package com.ai.cloud.skywalking.conf;

public class Config {

    public static class BuriedPoint{
        public static boolean PRINTF = true;
    }

    public static class Consumer {
        //最大消费线程数
        public static int MAX_CONSUMER = 2;
        //消费者最大等待时间
        public static long MAX_WAIT_TIME = 5L;
    }

    public static class Buffer {
        // 每个Buffer的最大个数
        public static int BUFFER_MAX_SIZE = 18000;

        // Buffer池的最大长度
        public static int POOL_MAX_LENGTH = 5;
    }

    public static class Sender {
        // 发送的最大条数
        public static int MAX_BUFFER_DATA_SIZE = 1;

        // 最大发送者的连接数阀比例
        public static int SEND_CONNECTION_THRESHOLD = 2;

        // 发送服务端配置
        public static String SENDER_SERVERS;

        // 是否开启发送
        public static boolean OFF = false;
    }
}