package org.skywalking.apm.agent.core.datacarrier.buffer;

/**
 * Created by wusheng on 2016/10/25.
 */
public enum BufferStrategy {
    /**
     * 阻塞模式
     */
    BLOCKING,
    /**
     * 复写模式
     */
    OVERRIDE,
    /**
     * 尝试写入模式，无法写入则返回写入失败
     */
    IF_POSSIBLE
}
