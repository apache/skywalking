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

package org.apache.skywalking.oap.trace.analyzer.v2.dsl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.source.Source;

/**
 * A source whose {@code payload} setter has three overloads, which logs every one a rule calls, in order, so a test
 * sees which one a {@code source {}} field chose.
 */
@ScopeDeclaration(id = TestOverloadedSource.SCOPE, name = "TestOverloadedSource")
public class TestOverloadedSource extends Source {
    public static final int SCOPE = 99_001;
    public static final List<String> CALLS = new ArrayList<>();

    public void setPayload(final Map<String, String> payload) {
        CALLS.add("payload map of " + payload.size());
    }

    public void setPayload(final List<String> payload) {
        CALLS.add("payload list of " + payload.size());
    }

    public void setPayload(final Collection<String> payload) {
        CALLS.add("payload collection of " + payload.size());
    }

    @Override
    public int scope() {
        return SCOPE;
    }

    @Override
    public String getEntityId() {
        return Const.EMPTY_STRING;
    }

    @Override
    public String toJson() {
        return "{}";
    }
}
