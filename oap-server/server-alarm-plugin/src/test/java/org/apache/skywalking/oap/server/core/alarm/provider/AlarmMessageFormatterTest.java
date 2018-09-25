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
import org.apache.skywalking.oap.server.core.source.Scope;
import org.junit.Assert;
import org.junit.Test;

public class AlarmMessageFormatterTest {
    @Test
    public void testStringFormatWithNoArg() {
        AlarmMessageFormatter formatter = new AlarmMessageFormatter("abc words {sdf");
        String message = formatter.format(new MetaInAlarm() {

            @Override public Scope getScope() {
                return null;
            }

            @Override public String getName() {
                return null;
            }

            @Override public String getIndicatorName() {
                return null;
            }

            @Override public int getId0() {
                return 0;
            }

            @Override public int getId1() {
                return 0;
            }
        });

        Assert.assertEquals("abc words {sdf", message);
    }

    @Test
    public void testStringFormatWithArg() {
        AlarmMessageFormatter formatter = new AlarmMessageFormatter("abc} words {name} - {id} .. {");
        String message = formatter.format(new MetaInAlarm() {

            @Override public Scope getScope() {
                return null;
            }

            @Override public String getName() {
                return "service";
            }

            @Override public String getIndicatorName() {
                return null;
            }

            @Override public int getId0() {
                return 1290;
            }

            @Override public int getId1() {
                return 0;
            }
        });
        Assert.assertEquals("abc} words service - 1290 .. {", message);
    }
}
