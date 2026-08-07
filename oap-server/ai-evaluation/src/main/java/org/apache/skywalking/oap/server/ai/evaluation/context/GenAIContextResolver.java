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

package org.apache.skywalking.oap.server.ai.evaluation.context;

import java.util.Map;
import lombok.Data;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.library.util.genai.GenAIModelMatcher;

public final class GenAIContextResolver {

    private GenAIContextResolver() {
    }

    public static Result resolve(final Map<String, String> tags) {
        String modelName = tags.get(GenAISemanticAttributes.RESPONSE_MODEL);
        String providerName = tags.get(GenAISemanticAttributes.PROVIDER_NAME);

        if (StringUtil.isBlank(providerName)) {
            providerName = tags.get(GenAISemanticAttributes.SYSTEM);
        }

        if (StringUtil.isBlank(providerName) && StringUtil.isNotBlank(modelName)) {
            providerName = GenAIModelMatcher.getInstance().match(modelName).getProvider();
        }

        return new Result(providerName, modelName);
    }

    @Data
    public static class Result {
        private final String providerName;
        private final String modelName;
    }
}
