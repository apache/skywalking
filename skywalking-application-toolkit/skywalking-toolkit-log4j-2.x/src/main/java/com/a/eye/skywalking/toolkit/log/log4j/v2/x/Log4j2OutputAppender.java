package com.a.eye.skywalking.toolkit.log.log4j.v2.x;

/**
 * Created by wusheng on 2016/12/11.
 */
public class Log4j2OutputAppender {
    /**
     * As default, append "TID: N/A" to the output message,
     * if sky-walking agent in active mode, append the real traceId in the recent Context, if existed, or empty String.
     *
     * @param toAppendTo origin output message.
     */
    public static void append(StringBuilder toAppendTo) {
        toAppendTo.append("TID: N/A");
    }
}
