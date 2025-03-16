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

package org.apache.skywalking.oap.server.core.alarm.provider.webhook;

import java.util.*;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHookSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHooksType;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@Setter
@Getter
@ToString
public class WebhookSettings extends AlarmHookSettings {
    private List<String> urls = new ArrayList<>();
    private Authorization authorization;


    public WebhookSettings(final String name,
                           final AlarmHooksType type,
                           final boolean isDefault) {
        super(name, type, isDefault);

    }

    @Builder
    @Data
    @ToString
    public static class Authorization {
        /**
         * @see WebhookAuthType
         */
        private final String type;
        private final String credentials;

        public void validate() {
            if (!WebhookAuthType.validate(type)) {
                throw new IllegalArgumentException("Unsupported authorization: " + type);
            }
            if (StringUtil.isEmpty(credentials)) {
                throw new IllegalArgumentException("Credentials cannot be null or empty");
            }
        }
    }

    public Map<String, String> getAuthHeaders(){
        HashMap<String, String> headers = new HashMap<>();
        if (authorization != null){
            headers.put("Authorization", "Bearer " + this.authorization.credentials);
        }
        return headers;
    }
}
