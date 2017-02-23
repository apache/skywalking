package com.a.eye.skywalking.api.util;

import com.a.eye.skywalking.api.conf.Constants;
import java.util.UUID;

public final class TraceIdGenerator {
    private static final ThreadLocal<Integer> ThreadTraceIdSequence = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

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
    public static String generate() {
        Integer seq = ThreadTraceIdSequence.get();
        seq++;
        ThreadTraceIdSequence.set(seq);

        return StringUtil.join('.',
            Constants.SDK_VERSION + "", System.currentTimeMillis() + "", PROCESS_UUID + "",
            MachineInfo.getProcessNo() + "", Thread.currentThread().getId() + "", seq + "");
    }
}
