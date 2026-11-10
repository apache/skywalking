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

package org.apache.skywalking.oap.trace.analyzer.v2.spi;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The TAL constructs that belong to an input rather than to the language, because each input builds differently:
 * an input that builds through span metadata overwrites the metadata and votes on the span's persistence; the native
 * input builds its outputs in the segment's build phase. An input names the ones it has in
 * {@link TALSourceTypeProvider#constructs()}; every other construct of the language belongs to every input.
 */
public enum TALConstruct {
    META("the meta root"),
    SERVICE("service"),
    INSTANCE("instance"),
    ENDPOINT("endpoint"),
    LAYER("layer"),
    TAG("tag"),
    PERSIST("persist {}"),
    PREPARE_NETWORK_ALIAS("prepareNetworkAlias"),
    BUILD_NETWORK_ALIAS("buildNetworkAlias"),
    PAIRING("the pairing root"),
    BUILD("build {}"),
    FIRST("first {}"),
    SEGMENT_DEF("segmentDef"),
    WRITE_NETWORK_ALIAS("writeNetworkAlias");

    /**
     * The constructs of an input that builds through span metadata, as OTLP does, which is what an input has unless
     * its provider says otherwise.
     */
    public static final Set<TALConstruct> SPAN_METADATA = Collections.unmodifiableSet(EnumSet.of(
        META, SERVICE, INSTANCE, ENDPOINT, LAYER, TAG, PERSIST, PREPARE_NETWORK_ALIAS, BUILD_NETWORK_ALIAS, PAIRING));

    /**
     * The constructs of the native input, which builds the agent analysis's outputs from the segment.
     */
    public static final Set<TALConstruct> NATIVE_SEGMENT = Collections.unmodifiableSet(EnumSet.of(
        BUILD, FIRST, SEGMENT_DEF, WRITE_NETWORK_ALIAS));

    private final String spelling;

    TALConstruct(final String spelling) {
        this.spelling = spelling;
    }

    /**
     * @return the construct as a rule writes it, for diagnostics
     */
    public String spelling() {
        return spelling;
    }
}
