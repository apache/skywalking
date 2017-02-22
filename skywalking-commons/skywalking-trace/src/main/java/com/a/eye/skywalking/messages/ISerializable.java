package com.a.eye.skywalking.messages;

/**
 * Created by wusheng on 2017/2/22.
 */
public interface ISerializable<T> {
    T serialize();

    void deserialize(T message);
}
