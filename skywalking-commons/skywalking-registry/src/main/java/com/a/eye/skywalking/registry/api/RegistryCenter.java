package com.a.eye.skywalking.registry.api;

import java.util.Properties;

/**
 * 主要用于注册中心的维护
 */
public interface RegistryCenter {

    /**
     * 主要用于storage启动注册使用，将自身IP和端口注册到注册中心
     *
     *
     * @param path 格式为:/storage_list/192.168.0.1:3400
     */
    void register(String path);

    /**
     * 主要用于routing节点在启动完成之后，读取和监听stroage节点列表
     *
     * @param path
     * @param listener
     */
    void subscribe(String path, NotifyListener listener);


    /**
     * 在注册和订阅之前，需要先启动注册中心
     *
     * @param centerConfig 配置参数
     */
    void start(Properties centerConfig);
}
