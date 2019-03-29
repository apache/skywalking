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

package org.apache.skywalking.oap.server.core.alarm;

import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Indicator notify service should be provided by Alarm Module provider, which can receive the indicator value, driven
 * by storage core.
 *
 * The alarm module provider could choose whether or how to do the alarm. Meanwhile, the storage core will provide the
 * standard persistence service for generated alarm, if the alarm engine wants the alarm to show in UI, please call
 * those to save.
 *
 * @author wusheng
 */
public interface IndicatorNotify extends Service {
    void notify(Indicator indicator);
}
