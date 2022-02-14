/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.e2e.controller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.e2e.KeyValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/alarm")
public class AlarmController {

    // Save all received alarm message from oap
    private List<AlarmMessage> alarmMessages = new ArrayList<>();

    @PostMapping("/receive")
    public String receiveAlarmMessage(@RequestBody List<AlarmMessage> data) {
        alarmMessages.addAll(data);
        return "success";
    }

    @PostMapping("/read")
    public Alarms readMessages() {
        return Alarms.builder().messages(alarmMessages).build();
    }

    /**
     * Alarm message represents the details of each alarm.
     */
    @Setter
    @Getter
    @NoArgsConstructor
    public static class AlarmMessage {
        private int scopeId;
        private String scope;
        private String name;
        private String id0;
        private String id1;
        private String ruleName;
        private String alarmMessage;
        private long startTime;
        private List<KeyValue> tags;
    }

    /**
     * Alarm wrapper
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alarms {
        private List<AlarmMessage> messages;
    }

}
