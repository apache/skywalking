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

package org.apache.skywalking.oap.server.library.util.genai;

import java.util.Map;
import lombok.Data;
import org.apache.skywalking.oap.server.library.util.StringUtil;

public final class GenAIContextResolver {

    private GenAIContextResolver() {
    }

    public static Result resolve(final Map<String, String> attributes) {
        return resolve(attributes, GenAIModelMatcher.getInstance());
    }

    static Result resolve(final Map<String, String> attributes,
                          final GenAIModelMatcher modelMatcher) {
        final String modelName = attributes.get(GenAISemanticAttributes.RESPONSE_MODEL);
        final String declaredProvider = attributes.get(GenAISemanticAttributes.PROVIDER_NAME);
        final String legacySystem = attributes.get(GenAISemanticAttributes.SYSTEM_NAME);
        final GenAIModelMatcher.MatchResult modelMatch = modelMatcher.match(modelName);

        final String providerName;
        if (StringUtil.isNotBlank(declaredProvider)) {
            providerName = declaredProvider;
        } else if (modelMatch.hasMatchedProvider()) {
            providerName = modelMatch.getProvider();
        } else if (StringUtil.isNotBlank(legacySystem)) {
            providerName = legacySystem;
        } else {
            providerName = modelMatch.getProvider();
        }
        return new Result(providerName, modelName);
    }

    @Data
    public static class Result {
        private final String providerName;
        private final String modelName;
    }
}
