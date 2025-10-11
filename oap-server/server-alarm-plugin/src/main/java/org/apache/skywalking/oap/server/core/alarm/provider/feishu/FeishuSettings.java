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

package org.apache.skywalking.oap.server.core.alarm.provider.feishu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHookSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHooksType;

@Setter
@Getter
@ToString
public class FeishuSettings extends AlarmHookSettings {

    private String textTemplate;
    private String recoveryTextTemplate;
    @Builder.Default
    private List<WebHookUrl> webhooks = new ArrayList<>();

    public FeishuSettings(final String name,
                          final AlarmHooksType type,
                          final boolean isDefault) {
        super(name, type, isDefault);
    }

    @AllArgsConstructor
    @Setter
    @Getter
    @ToString
    public static class WebHookUrl {
        private final String secret;
        private final String url;
    }
}
