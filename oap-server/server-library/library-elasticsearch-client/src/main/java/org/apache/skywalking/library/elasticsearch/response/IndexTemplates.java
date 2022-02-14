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
 */

package org.apache.skywalking.library.elasticsearch.response;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public final class IndexTemplates implements Iterable<IndexTemplate> {
    private final Map<String, IndexTemplate> templates;

    public Optional<IndexTemplate> get(String name) {
        final Map<String, IndexTemplate> templates = getTemplates();
        if (templates == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(templates.get(name));
    }

    @Override
    public Iterator<IndexTemplate> iterator() {
        if (getTemplates() == null) {
            return Collections.emptyIterator();
        }
        return getTemplates().values().iterator();
    }
}
