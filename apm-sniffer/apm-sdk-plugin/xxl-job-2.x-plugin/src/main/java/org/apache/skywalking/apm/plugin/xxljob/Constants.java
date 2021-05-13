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

package org.apache.skywalking.apm.plugin.xxljob;

import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;

public class Constants {

    public static final String XXL_IJOB_HANDLER = "com.xxl.job.core.handler.IJobHandler";
    public static final String XXL_GLUE_JOB_HANDLER = "com.xxl.job.core.handler.impl.GlueJobHandler";
    public static final String XXL_SCRIPT_JOB_HANDLER = "com.xxl.job.core.handler.impl.ScriptJobHandler";
    public static final String XXL_METHOD_JOB_HANDLER = "com.xxl.job.core.handler.impl.MethodJobHandler";

    public static final AbstractTag JOB_PARAM = Tags.ofKey("jobParam");
}
