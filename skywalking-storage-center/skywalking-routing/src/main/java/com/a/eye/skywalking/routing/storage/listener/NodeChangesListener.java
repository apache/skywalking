package com.a.eye.skywalking.routing.storage.listener;

import com.a.eye.skywalking.registry.api.RegistryNode;

import java.util.List;

/**
 * Created by xin on 2016/11/27.
 */
public interface NodeChangesListener {
    void notify(List<RegistryNode> registryNodes);
}
