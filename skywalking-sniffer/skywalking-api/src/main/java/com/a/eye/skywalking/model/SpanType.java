package com.a.eye.skywalking.model;

/**
 * Created by wusheng on 2016/11/26.
 */
public interface SpanType {
    String LOCAL = "0";
    String RPC_CLIENT = "1";
    String RPC_SERVER = "2";
}
