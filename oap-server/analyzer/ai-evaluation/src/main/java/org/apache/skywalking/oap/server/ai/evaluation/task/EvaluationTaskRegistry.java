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

package org.apache.skywalking.oap.server.ai.evaluation.task;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.library.util.StringUtil;

public class EvaluationTaskRegistry {
    private final List<EvaluationTask> tasks;

    public EvaluationTaskRegistry(final List<EvaluationTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            this.tasks = Collections.emptyList();
            return;
        }

        final Map<String, EvaluationTask> registeredTasks = new LinkedHashMap<>();
        for (EvaluationTask task : tasks) {
            if (task == null || StringUtil.isEmpty(task.getName())) {
                continue;
            }
            registeredTasks.put(task.getName(), task);
        }
        this.tasks = List.copyOf(registeredTasks.values());
    }

    public List<EvaluationTask> tasks() {
        return tasks;
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }

}
