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
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.remote.grpc.service.selector;

import java.util.List;
import org.skywalking.apm.collector.core.data.AbstractHashMessage;
import org.skywalking.apm.collector.remote.service.RemoteClient;

/**
 * @author peng-yongsheng
 */
public class HashCodeSelector implements RemoteClientSelector {

    @Override public RemoteClient select(List<RemoteClient> clients, Object message) {
        if (message instanceof AbstractHashMessage) {
            AbstractHashMessage hashMessage = (AbstractHashMessage)message;
            int size = clients.size();
            int selectIndex = Math.abs(hashMessage.getHashCode()) % size;
            return clients.get(selectIndex);
        } else {
            throw new IllegalArgumentException("the message send into HashCodeSelector must implementation of AbstractHashMessage, the message object class is: " + message.getClass().getName());
        }
    }
}
