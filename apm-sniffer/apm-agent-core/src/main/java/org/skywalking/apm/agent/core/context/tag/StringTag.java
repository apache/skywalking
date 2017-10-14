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

package org.skywalking.apm.agent.core.context.tag;

import org.skywalking.apm.agent.core.context.trace.AbstractSpan;

/**
 * A subclass of {@link AbstractTag},
 * represent a tag with a {@link String} value.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public class StringTag extends AbstractTag<String> {
    public StringTag(String tagKey) {
        super(tagKey);
    }

    @Override
    public void set(AbstractSpan span, String tagValue) {
        span.tag(key, tagValue);
    }
}
