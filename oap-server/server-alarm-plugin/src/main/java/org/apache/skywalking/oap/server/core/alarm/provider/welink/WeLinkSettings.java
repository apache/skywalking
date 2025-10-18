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

package org.apache.skywalking.oap.server.core.alarm.provider.welink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHookSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHooksType;

@Setter
@Getter
@ToString
public class WeLinkSettings extends AlarmHookSettings {

    private String textTemplate;
    private String recoveryTextTemplate;
    private List<WebHookUrl> webhooks = new ArrayList<>();

    public WeLinkSettings(final String name,
                          final AlarmHooksType type,
                          final boolean isDefault) {
        super(name, type, isDefault);
    }

    @AllArgsConstructor
    @Setter
    @Getter
    @ToString
    public static class WebHookUrl {
        // The unique identity of the application, used for interface authentication to obtain access_token
        private final String clientId;
        // The application key is used for interface authentication to obtain access_token
        private final String clientSecret;
        // The url get access token
        private final String accessTokenUrl;
        // The url to send message
        private final String messageUrl;
        // Name display in group
        private final String robotName;
        // The groupIds message to send
        private final String groupIds;

        public static WebHookUrl generateFromMap(Map<String, String> params) {
            String clientId = params.get("client-id");
            String clientSecret = params.get("client-secret");
            String accessTokenUrl = params.get("access-token-url");
            String messageUrl = params.get("message-url");
            String groupIds = params.get("group-ids");
            String robotName = params.getOrDefault("robot-name", "robot");
            return new WebHookUrl(clientId, clientSecret, accessTokenUrl, messageUrl,
                                  robotName, groupIds
            );
        }
    }
}
