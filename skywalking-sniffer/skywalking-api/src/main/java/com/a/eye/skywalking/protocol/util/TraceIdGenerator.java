package com.a.eye.skywalking.protocol.util;

import java.util.UUID;

import com.a.eye.skywalking.conf.Constants;
import com.a.eye.skywalking.network.grpc.TraceId;

public final class TraceIdGenerator {
    private static final ThreadLocal<Integer> ThreadTraceIdSequence = new ThreadLocal<Integer>();

    private static final int PROCESS_UUID;

    static {
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        PROCESS_UUID = uuid.substring(uuid.length() - 7).hashCode();
    }

    private TraceIdGenerator() {
    }

    /**
     * TraceId由以下规则组成<br/>
     * version号 + 1位时间戳（毫秒数） + 1位进程随机号（UUID后7位） + 1位进程数号 + 1位线程号 + 1位线程内序号
     * <p>
     * 注意：这里的位，是指“.”作为分隔符所占的位数，非字符串长度的位数。
     * TraceId为6个片段组成的数组
     *
     * @return
     */
    public static TraceId generate() {
        Integer seq = ThreadTraceIdSequence.get();
        if (seq == null || seq == 10000 || seq > 10000) {
            seq = 0;
        }
        seq++;
        ThreadTraceIdSequence.set(seq);

        return TraceId.newBuilder().addSegments(Constants.SDK_VERSION)
                .addSegments(System.currentTimeMillis()).addSegments(PROCESS_UUID)
                .addSegments(BuriedPointMachineUtil.getProcessNo())
                .addSegments(Thread.currentThread().getId()).addSegments(seq).build();
    }
}
