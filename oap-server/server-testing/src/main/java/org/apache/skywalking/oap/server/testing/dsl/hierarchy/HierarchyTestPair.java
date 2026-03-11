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

package org.apache.skywalking.oap.server.testing.dsl.hierarchy;

import lombok.Getter;

/**
 * A test pair for hierarchy rule comparison: an upper service and a lower service,
 * with the expected match result.
 */
@Getter
public final class HierarchyTestPair {
    private final String description;
    private final String upperName;
    private final String upperShortName;
    private final String lowerName;
    private final String lowerShortName;
    private final Boolean expected;

    public HierarchyTestPair(final String description,
                             final String upperName, final String upperShortName,
                             final String lowerName, final String lowerShortName,
                             final Boolean expected) {
        this.description = description;
        this.upperName = upperName;
        this.upperShortName = upperShortName;
        this.lowerName = lowerName;
        this.lowerShortName = lowerShortName;
        this.expected = expected;
    }
}
