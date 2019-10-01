/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.core.alarm.provider;

import org.apache.http.HttpEntity;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;

import java.util.List;

/**
 * @author muyun12
 */
public interface RemoteEndpoint {

    /**
     * webhook remote endpoint key
     * <pre  class="code">
     *  webhooks:
     *     default:
     *       - http://127.0.0.1/notify/
     *       - http://127.0.0.1/go-wechat/
     *     dingtalk:
     *       - http://127.0.0.1/notify/
     * </pre>
     * <b>default</b> and <b>dingtalk</b> is the key.
     *
     * @returns
     */
    String getRemoteEndpointKey();

    /**
     * transform alarm message to custom format
     * @param alarmMessage
     * @return
     */
    HttpEntity transformAlarmMessage(List<AlarmMessage> alarmMessage);
}
