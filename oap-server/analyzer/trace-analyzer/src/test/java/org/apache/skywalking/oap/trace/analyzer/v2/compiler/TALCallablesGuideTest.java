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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every method TAL's list names is in its guide, so the list and the guide cannot drift.
 */
class TALCallablesGuideTest {

    @Test
    void theGuideListsEveryMethodOfTheList() throws Exception {
        final String guide = new String(Files.readAllBytes(Paths.get("../../../docs/en/concepts-and-designs/tal.md")));
        final int start = guide.indexOf("#### What a rule can call");
        assertTrue(start >= 0, "the guide's section is gone");
        final int end = guide.indexOf("\n#### ", start + 1);
        final String section = guide.substring(start, end < 0 ? guide.length() : end);
        for (final Map<Class<?>, Set<String>> table : new Map[] {
            TALCallables.get().declaredMethods(), TALCallables.get().declaredStatics()}) {
            table.forEach((type, names) -> names.forEach(name -> assertTrue(
                // the whole name, as `name`, `name(...)` or `Type.name`: replace is not found in replaceAll
                Pattern.compile("[`.]" + Pattern.quote(name) + "(?![A-Za-z0-9_])").matcher(section).find(),
                type.getSimpleName() + "." + name + "() is in the list but not in the guide")));
        }
    }
}
