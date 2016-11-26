package com.a.eye.skywalking.model;

/**
 * Created by wusheng on 2016/11/26.
 */
public interface SpanType {
    int LOCAL = 0;
    int RPC_CLIENT = 1;
    int RPC_SERVER = 2;
}
