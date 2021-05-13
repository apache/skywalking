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

package org.apache.skywalking.apm.agent.core.plugin.match.logical;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.match.IndirectMatch;

/**
 * Match classes by multiple criteria with AND conjunction
 */
public class LogicalAndMatch implements IndirectMatch {
    private final IndirectMatch[] indirectMatches;

    /**
     * Don't instantiate this class directly, use {@link LogicalMatchOperation} instead
     *
     * @param indirectMatches the matching criteria to conjunct with AND
     */
    LogicalAndMatch(final IndirectMatch... indirectMatches) {
        this.indirectMatches = indirectMatches;
    }

    @Override
    public ElementMatcher.Junction buildJunction() {
        ElementMatcher.Junction junction = null;

        for (final IndirectMatch indirectMatch : indirectMatches) {
            if (junction == null) {
                junction = indirectMatch.buildJunction();
            } else {
                junction = junction.and(indirectMatch.buildJunction());
            }
        }

        return junction;
    }

    @Override
    public boolean isMatch(final TypeDescription typeDescription) {
        for (final IndirectMatch indirectMatch : indirectMatches) {
            if (!indirectMatch.isMatch(typeDescription)) {
                return false;
            }
        }

        return true;
    }

}
