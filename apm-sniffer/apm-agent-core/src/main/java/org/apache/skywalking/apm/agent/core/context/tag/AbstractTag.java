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


package org.apache.skywalking.apm.agent.core.context.tag;

import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

public abstract class AbstractTag<T> {
    /**
     * The key of this Tag.
     */
    protected final String key;

    public AbstractTag(String tagKey) {
        this.key = tagKey;
    }

    protected abstract void set(AbstractSpan span, T tagValue);

    /**
     * @return the key of this tag.
     */
    public String key() {
        return this.key;
    }
}
