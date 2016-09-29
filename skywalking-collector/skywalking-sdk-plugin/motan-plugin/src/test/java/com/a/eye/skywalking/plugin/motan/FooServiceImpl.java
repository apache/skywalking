package com.a.eye.skywalking.plugin.motan;

/**
 * Created by xin on 16/9/27.
 */
public class FooServiceImpl implements FooService {

    public String hello(String name, String value) {
        System.out.println(name + " invoked rpc service");
        return "hello " + name + " " + value;
    }
}
