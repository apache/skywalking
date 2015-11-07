package com.ai.cloud.skywalking.buffer.config;

public class BufferConfig {
    // 最大消费线程
    public static int MAX_WORKER = 5;

    // 桶大小
    public static int GROUP_MAX_SIZE = 1000;

    // 桶数量
    public static int POOL_MAX_SIZE = 30;

    // 发送的最大长度
    public static int MAX_LENGTH = 512 * 1024;

    // 发送的最大条数
    public static int SEND_MAX_SIZE = 1;

    // 最大发送者的连接数阀值
    public static int SEND_CONNECTION_THRESHOLD = 2;
}
