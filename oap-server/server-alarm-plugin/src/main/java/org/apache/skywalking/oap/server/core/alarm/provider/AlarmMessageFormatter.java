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

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.alarm.MetaInAlarm;

/**
 * This is a formatter especially for alarm message.
 * <p>
 * Format string in alarm-settings.yml, such as:
 * <p>
 * - Successful rate of endpoint {name} is lower than 75%
 */
public class AlarmMessageFormatter {
    private List<String> formatSegments;
    private List<ValueFrom> valueFroms;

    public AlarmMessageFormatter(String format) {
        if (format == null) {
            format = "";
        }
        formatSegments = new ArrayList<>();
        this.valueFroms = new ArrayList<>();
        boolean match = false;
        int idx = 0;
        do {
            match = false;
            int start = format.indexOf("{", idx);
            if (start > -1) {
                int end = format.indexOf("}", start);
                if (end > -1) {

                    String name = format.substring(start + 1, end);
                    switch (name) {
                        case "id":
                            valueFroms.add(ValueFrom.ID);
                            break;
                        case "name":
                            valueFroms.add(ValueFrom.NAME);
                            break;
                        default:
                            throw new IllegalArgumentException("Var [" + name + "] in alarm message [" + format + "] is illegal");
                    }
                    formatSegments.add(format.substring(idx, start));
                    idx = end + 1;
                    match = true;
                }
            }

            if (!match) {
                formatSegments.add(format.substring(idx));
            }
        }
        while (match);
    }

    public String format(MetaInAlarm meta) {
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < formatSegments.size(); i++) {
            message.append(formatSegments.get(i));
            if (i != formatSegments.size() - 1) {
                switch (valueFroms.get(i)) {
                    case ID:
                        message.append(meta.getId0());
                        break;
                    case NAME:
                        message.append(meta.getName());
                }
            }
        }
        return message.toString();
    }

    private enum ValueFrom {
        ID, NAME
    }
}
