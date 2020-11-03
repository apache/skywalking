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

import java.util.Objects;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

public abstract class AbstractTag<T> {

    private int id;

    private boolean canOverwrite;
    /**
     * The key of this Tag.
     */
    protected final String key;

    public AbstractTag(int id, String tagKey, boolean canOverwrite) {
        this.id = id;
        this.key = tagKey;
        this.canOverwrite = canOverwrite;
    }

    public AbstractTag(String key) {
        this(-1, key, false);
    }

    protected abstract void set(AbstractSpan span, T tagValue);

    /**
     * @return the key of this tag.
     */
    public String key() {
        return this.key;
    }

    public boolean sameWith(AbstractTag<T> tag) {
        return canOverwrite && this.id == tag.id;
    }

    public int getId() {
        return id;
    }

    public boolean isCanOverwrite() {
        return canOverwrite;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AbstractTag))
            return false;
        final AbstractTag<?> that = (AbstractTag<?>) o;
        return getId() == that.getId() &&
            isCanOverwrite() == that.isCanOverwrite() &&
            key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), isCanOverwrite(), key);
    }
}
