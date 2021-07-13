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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@ToString
public class WeLinkSettings {

    private String textTemplate;
    @Builder.Default
    private List<WebHookUrl> webhooks = new ArrayList<>();

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
            String clientId = params.get("client_id");
            String clientSecret = params.get("client_secret");
            String accessTokenUrl = params.get("access_token_url");
            String messageUrl = params.get("message_url");
            String groupIds = params.get("group_ids");
            String robotName = params.getOrDefault("robot_name", "robot");
            return new WebHookUrl(clientId, clientSecret, accessTokenUrl, messageUrl,
                                  robotName, groupIds
            );
        }
    }
}
