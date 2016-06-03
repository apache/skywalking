package com.ai.cloud.skywalking.agent.test;

import com.ai.cloud.skywalking.agent.test.utils.JedisUtils;

/**
 * Created by xin on 16-6-3.
 */
public class AgentTest {

    public static void main(String[] args) {
        System.out.println("Begin...");
        JedisUtils.setData("testKey1", "testKey2");

        System.out.println("testKey1 = " + JedisUtils.getData("testKey1"));

        JedisUtils.expire("testKey1");

        System.out.println("End.....");
    }
}
