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

package org.skywalking.apm.ui.tools;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * @author peng-yongsheng
 */
@Component
public class ServerSelector {

    private int index = 0;

    public String select(List<String> serverList) {
        String server = null;
        int tryCnt = 0;
        do {
            int size = serverList.size();
            int selectIndex = Math.abs(index) % size;
            index ++;
            try {
                server = serverList.get(selectIndex);
            } catch (Exception e) {
            }
            if (null == server) {
                tryCnt++;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            } else {
                return server;
            }
        } while (tryCnt < 3);
        throw new RuntimeException("select server fail.");
    }
}
