package com.a.eye.skywalking.registry.api;

/**
 * Created by xin on 2016/11/9.
 */
public interface Register {

    void subscribe(NotifyListener listener);

    void unSubscribe();

    void registry();
}
