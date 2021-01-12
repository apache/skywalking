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

package org.apache.skywalking.apm.toolkit.activation.util;

import java.util.Map;

import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.util.CustomizeExpression;
import org.apache.skywalking.apm.toolkit.trace.Tag;

public class TagUtil {
    public static void tagParamsSpan(final AbstractSpan span, final Map<String, Object> context,
                                     final Tag tag) {
        new StringTag(tag.key()).set(span, CustomizeExpression.parseExpression(tag.value(), context));
    }

    public static void tagReturnSpanSpan(final AbstractSpan span, final Map<String, Object> context,
                                         final Tag tag) {
        new StringTag(tag.key()).set(span, CustomizeExpression.parseReturnExpression(tag.value(), context));
    }

    public static Boolean isReturnTag(String expression) {
        String[] es = expression.split("\\.");
        return "returnedObj".equals(es[0]);
    }
}
