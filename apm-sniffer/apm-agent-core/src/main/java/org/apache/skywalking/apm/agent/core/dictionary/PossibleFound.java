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

package org.apache.skywalking.apm.agent.core.dictionary;

/**
 * The <code>PossibleFound</code> represents a value, which may needEnhance or not.
 */
public abstract class PossibleFound {
    private boolean found;
    private int value;

    PossibleFound(int value) {
        this.found = true;
        this.value = value;
    }

    PossibleFound() {
        this.found = false;
    }

    public void doInCondition(Found condition1, NotFound condition2) {
        if (found) {
            condition1.doProcess(value);
        } else {
            condition2.doProcess();
        }
    }

    public Object doInCondition(FoundAndObtain condition1, NotFoundAndObtain condition2) {
        if (found) {
            return condition1.doProcess(value);
        } else {
            return condition2.doProcess();
        }
    }

    public interface Found {
        void doProcess(int value);
    }

    public interface NotFound {
        void doProcess();
    }

    public interface FoundAndObtain {
        Object doProcess(int value);
    }

    public interface NotFoundAndObtain {
        Object doProcess();
    }
}
