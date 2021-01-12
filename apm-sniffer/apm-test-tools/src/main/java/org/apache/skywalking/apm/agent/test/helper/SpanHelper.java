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

package org.apache.skywalking.apm.agent.test.helper;

import java.util.Collections;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;

public class SpanHelper {
    public static int getParentSpanId(AbstractSpan tracingSpan) {
        try {
            return FieldGetter.get2LevelParentFieldValue(tracingSpan, "parentSpanId");
        } catch (Exception e) {
            try {
                return FieldGetter.getParentFieldValue(tracingSpan, "parentSpanId");
            } catch (Exception e1) {

            }
        }

        return -9999;
    }

    public static List<LogDataEntity> getLogs(AbstractSpan tracingSpan) {
        try {
            List<LogDataEntity> logs = FieldGetter.get2LevelParentFieldValue(tracingSpan, "logs");
            if (logs != null) {
                return logs;
            }
        } catch (Exception e) {
            try {
                List<LogDataEntity> logs = FieldGetter.getParentFieldValue(tracingSpan, "logs");
                if (logs != null) {
                    return logs;
                }
            } catch (Exception e1) {

            }
        }

        return Collections.emptyList();
    }

    public static List<TagValuePair> getTags(AbstractSpan tracingSpan) {
        try {
            List<TagValuePair> tags = FieldGetter.get2LevelParentFieldValue(tracingSpan, "tags");
            if (tags != null) {
                return tags;
            }
        } catch (Exception e) {
            try {
                List<TagValuePair> tags = FieldGetter.getParentFieldValue(tracingSpan, "tags");
                if (tags != null) {
                    return tags;
                }
            } catch (Exception e1) {

            }
        }

        return Collections.emptyList();
    }

    public static SpanLayer getLayer(AbstractSpan tracingSpan) {
        try {
            return FieldGetter.get2LevelParentFieldValue(tracingSpan, "layer");
        } catch (Exception e) {
            try {
                return FieldGetter.getParentFieldValue(tracingSpan, "layer");
            } catch (Exception e1) {

            }
        }

        return null;
    }

    public static String getComponentName(AbstractSpan tracingSpan) {
        try {
            return FieldGetter.get2LevelParentFieldValue(tracingSpan, "componentName");
        } catch (Exception e) {
            try {
                return FieldGetter.getParentFieldValue(tracingSpan, "componentName");
            } catch (Exception e1) {

            }
        }

        return null;
    }

    public static int getComponentId(AbstractSpan tracingSpan) {
        try {
            return FieldGetter.get2LevelParentFieldValue(tracingSpan, "componentId");
        } catch (Exception e) {
            try {
                return FieldGetter.getParentFieldValue(tracingSpan, "componentId");
            } catch (Exception e1) {

            }
        }

        return -1;
    }

    public static boolean getErrorOccurred(AbstractSpan tracingSpan) {
        try {
            return FieldGetter.get2LevelParentFieldValue(tracingSpan, "errorOccurred");
        } catch (Exception e) {
            try {
                return FieldGetter.getParentFieldValue(tracingSpan, "errorOccurred");
            } catch (Exception e1) {

            }
        }

        return false;
    }
}
