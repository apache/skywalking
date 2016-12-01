package com.a.eye.skywalking.routing.router;

/**
 * Created by xin on 2016/12/1.
 */
public class StringFactory implements com.lmax.disruptor.EventFactory<StringBuilder> {
    @Override
    public StringBuilder newInstance() {
        return new StringBuilder();
    }
}
