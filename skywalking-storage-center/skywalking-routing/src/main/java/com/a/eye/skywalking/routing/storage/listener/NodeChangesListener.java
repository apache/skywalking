package com.a.eye.skywalking.routing.storage.listener;

import java.util.List;

/**
 * Created by xin on 2016/11/27.
 */
public interface NodeChangesListener {

    void notify(List<String> url, NotifyListenerImpl.ChangeType type);
}
