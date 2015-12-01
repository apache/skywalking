package com.ai.cloud.skywalking.conf;

public class Config {

	public static class SkyWalking {
		public static String USER_ID;

		public static String APPLICATION_ID;
	}

	public static class BuriedPoint {
		// 是否打印埋点信息
		public static boolean PRINTF = false;

		public static int MAX_EXCEPTION_STACK_LENGTH = 4000;

		// Business Key 最大长度
		public static int BUSINESSKEY_MAX_LENGTH = 300;
	}

	public static class Consumer {
		// 最大消费线程数
		public static int MAX_CONSUMER = 2;
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
		// 最大发送者的连接数阀比例
		public static int CONNECT_PERCENT = 50;

		// 发送服务端配置
		public static String SERVERS_ADDR;

		// 是否开启发送
		public static boolean IS_OFF = false;

		// 发送的最大长度
		public static int MAX_SEND_LENGTH = 18500;

		public static long RETRY_GET_SENDER_WAIT_INTERVAL = 2000L;

	}

	public static class SenderChecker {

		// 检查周期时间
		public static long CHECK_POLLING_TIME = 200L;
	}
}