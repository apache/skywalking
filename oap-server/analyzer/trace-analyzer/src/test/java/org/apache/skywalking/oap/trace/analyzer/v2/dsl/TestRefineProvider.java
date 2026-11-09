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
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;

/**
 * Overloads whose return types refine one step per call, so a segment variable assigned from its own value needs
 * several generations to settle its type.
 */
public class TestRefineProvider implements TALFunctionProvider {
    @Override
    public String module() {
        return "refine-side";
    }

    @Override
    public boolean supportsInputType(final String inputType) {
        return TALFunctionProvider.SW_NATIVE_INPUT_TYPE.equals(inputType);
    }

    public Collection<String> refine(final Object value) {
        return new ArrayList<>();
    }

    public List<String> refine(final Collection<String> value) {
        return new ArrayList<>();
    }

    public ArrayList<String> refine(final List<String> value) {
        return new ArrayList<>();
    }

    public ArrayList<String> refine(final ArrayList<String> value) {
        return new ArrayList<>();
    }

    public String choose(final List<String> value) {
        return "list";
    }

    public String choose(final ArrayList<String> value) {
        return "arraylist";
    }

    public String describe(final Collection<String> value) {
        return "collection";
    }

    public int describe(final List<String> value) {
        return 1;
    }
}
