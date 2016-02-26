package com.ai.cloud.skywalking.util;

import java.util.UUID;

import com.ai.cloud.skywalking.conf.Constants;

public final class TraceIdGenerator {
	private static final ThreadLocal<Integer> ThreadTraceIdSequence = new ThreadLocal<Integer>();

	private static final String PROCESS_UUID;

	static {
		String uuid = UUID.randomUUID().toString().replaceAll("-", "");
		PROCESS_UUID = uuid.substring(uuid.length() - 7);
	}

	private TraceIdGenerator() {
	}

	/**
	 * TraceId由以下规则组成<br/>
	 * 2位version号 + 1位时间戳（毫秒数） + 1位进程随机号（UUID后7位） + 1位进程数号 + 1位线程号 + 1位线程内序号
	 * 
	 * 注意：这里的位，是指“.”作为分隔符所占的位数，非字符串长度的位数。
	 * TraceId为不定长字符串，但保证在分布式集群条件下的唯一性
	 * 
	 * @return
	 */
	public static String generate() {
		Integer seq = ThreadTraceIdSequence.get();
		if (seq == null || seq == 10000 || seq > 10000) {
			seq = 0;
		}
		seq++;
		ThreadTraceIdSequence.set(seq);

		return Constants.SDK_VERSION 
				+ "." + System.currentTimeMillis() 
				+ "." + PROCESS_UUID 
				+ "." + BuriedPointMachineUtil.getProcessNo()
				+ "." + Thread.currentThread().getId() 
				+ "." + seq;
	}
}
