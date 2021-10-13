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

package org.apache.skywalking.oal.rt.parser;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * FROM statement in the OAL script
 */
@Setter
@Getter
public class FromStmt {
    /**
     * Source name in the FROM statement
     */
    private String sourceName;
    /**
     * source id according to {@link #sourceName}
     */
    private int sourceScopeId;
    /**
     * Attribute accessor
     */
    private List<String> sourceAttribute = new ArrayList<>();
    /**
     * Type cast function if exists. NULL as default, means no cast.
     */
    private String sourceCastType;
}
