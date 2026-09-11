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

package org.apache.skywalking.e2e.mockllm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A stand-in for the OpenAI-compatible LLM endpoints the GenAI e2e cases talk to. It serves both the
 * chat completions the instrumented applications call, and the judge completions OAP's AI evaluation
 * calls, so no case needs a real LLM provider.
 */
@SpringBootApplication
public class MockLLMServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockLLMServerApplication.class, args);
    }
}
