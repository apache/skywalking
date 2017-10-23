/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking-ui
 */

package org.skywalking.apm.ui.creator;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.ui.tools.ServerSelector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author peng-yongsheng
 */
@Component
public class UrlCreator {

    private List<String> servers;

    private volatile boolean inited = false;

    private Object waiter = new Object();

    public UrlCreator() {
        servers = new ArrayList<>();
    }

    @Autowired
    private ServerSelector serverSelector;

    public String compound(String urlSuffix) {
        if (!inited) {
            synchronized (waiter) {
                try {
                    waiter.wait(100);
                } catch (InterruptedException e) {
                }
            }
        }
        String server = serverSelector.select(servers);
        return "http://" + server + urlSuffix;
    }

    public void updateServerList(List<String> servers) {
        if (null == servers || servers.isEmpty()) {
            return;
        }
        try {
            this.servers.clear();
            this.servers.addAll(servers);
        } finally {
            inited = true;
            synchronized (waiter) {
                waiter.notifyAll();
            }
        }
    }
}
