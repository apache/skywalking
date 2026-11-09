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

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.source.Source;

/**
 * A source whose fields take a Layer, an enum, a boxed number, a primitive and a list, which {@code source {}} field
 * statements convert to.
 */
@ScopeDeclaration(id = TestTypedSource.SCOPE, name = "TestTypedSource")
@Getter
@Setter
public class TestTypedSource extends Source {
    public static final int SCOPE = 99_002;

    private Layer kind;
    private DetectPoint point;
    private Integer hits;
    private long rate;
    private List<String> names;

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
