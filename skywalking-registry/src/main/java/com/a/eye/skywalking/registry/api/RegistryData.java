package com.a.eye.skywalking.registry.api;

/**
 * 存储注册项的数据
 * 注册项包括：注册的目录（支持多级，以"/"分割）以及注册值
 */
public interface RegistryData {

    String getPath();

    String getValue();
}
