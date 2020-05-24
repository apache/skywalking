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

package org.apache.skywalking.apm.toolkit.meter;

import java.util.List;
import java.util.Objects;

public class BaseMeter {

    private String name;
    private List<Tag> tags;

    public BaseMeter(String name, List<Tag> tags) {
        this.name = name;
        this.tags = tags;
    }

    public String getName() {
        return name;
    }

    public String getTag(String name) {
        for (Tag tag : tags) {
            if (Objects.equals(tag.getName(), name)) {
                return tag.getValue();
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseMeter baseMeter = (BaseMeter) o;
        return Objects.equals(name, baseMeter.name) &&
            Objects.equals(tags, baseMeter.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, tags);
    }
}
