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

import org.apache.skywalking.oap.server.core.alarm.MetaInAlarm;
import org.junit.Assert;
import org.junit.Test;

public class AlarmMessageFormatterTest {
    @Test
    public void testStringFormatWithNoArg() {
        AlarmMessageFormatter formatter = new AlarmMessageFormatter("abc words {sdf");
        String message = formatter.format(new MetaInAlarm() {

            @Override
            public String getScope() {
                return "SERVICE";
            }

            @Override
            public int getScopeId() {
                return -1;
            }

            @Override
            public String getName() {
                return null;
            }

            @Override
            public String getMetricsName() {
                return null;
            }

            @Override
            public String getId0() {
                return "";
            }

            @Override
            public String getId1() {
                return "";
            }
        });

        Assert.assertEquals("abc words {sdf", message);
    }

    @Test
    public void testStringFormatWithArg() {
        AlarmMessageFormatter formatter = new AlarmMessageFormatter("abc} words {name} - {id} .. {");
        String message = formatter.format(new MetaInAlarm() {

            @Override
            public String getScope() {
                return "SERVICE";
            }

            @Override
            public int getScopeId() {
                return -1;
            }

            @Override
            public String getName() {
                return "service";
            }

            @Override
            public String getMetricsName() {
                return null;
            }

            @Override
            public String getId0() {
                return "1290";
            }

            @Override
            public String getId1() {
                return "";
            }
        });
        Assert.assertEquals("abc} words service - 1290 .. {", message);
    }
}
