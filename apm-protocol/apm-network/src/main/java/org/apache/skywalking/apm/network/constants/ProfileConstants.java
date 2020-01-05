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
package org.apache.skywalking.apm.network.constants;

/**
 * profile task limit constants
 *
 * @author MrPro
 */
public class ProfileConstants {

    /**
     * Monitor duration must greater than 1 minutes
     */
    public static final int TASK_DURATION_MIN_MINUTE = 1;

    /**
     * The duration of the monitoring task cannot be greater than 15 minutes
     */
    public static final int TASK_DURATION_MAX_MINUTE = 15;

    /**
     * Dump period must be greater than or equals 10 milliseconds
     */
    public static final int TASK_DUMP_PERIOD_MIN_MILLIS = 10;

    /**
     * Max sampling count must less than 10
     */
    public static final int TASK_MAX_SAMPLING_COUNT = 10;

}
