/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.core.framework;

import java.util.List;

/**
 * @author peng-yongsheng
 */
public class PriorityDecision implements Decision {

    public Object decide(List<Priority> source) {
        return source.get(0);
    }

    public static class Priority {
        private final int value;
        private final Object object;

        public Priority(int value, Object object) {
            this.value = value;
            this.object = object;
        }

        public int getValue() {
            return value;
        }

        public Object getObject() {
            return object;
        }
    }
}
