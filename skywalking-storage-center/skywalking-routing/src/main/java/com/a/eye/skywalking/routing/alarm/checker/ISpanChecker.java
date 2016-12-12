package com.a.eye.skywalking.routing.alarm.checker;


import com.a.eye.skywalking.routing.disruptor.ack.AckSpanHolder;

/**
 * Created by xin on 2016/12/8.
 */
public interface ISpanChecker {
    CheckResult check(AckSpanHolder span);
}
