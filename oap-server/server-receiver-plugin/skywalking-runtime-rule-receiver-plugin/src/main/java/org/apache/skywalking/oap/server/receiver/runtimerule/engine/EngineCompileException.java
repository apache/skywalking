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

package org.apache.skywalking.oap.server.receiver.runtimerule.engine;

/**
 * Thrown by {@link RuleEngine#compile} when the engine could not produce a valid
 * {@link CompiledDSL}. The engine is responsible for cleaning its own partial state before
 * throwing — by the time this exception reaches the orchestrator, no engine-side bookkeeping
 * remains from the failed attempt and the prior bundle is still serving.
 *
 * <p>The orchestrator catches this, stamps the snapshot's {@code applyError} with
 * {@link Throwable#getMessage()} (which carries the underlying applier's diagnostics), and
 * surfaces the failure to the REST caller / lets the next dslManager tick retry.
 */
public final class EngineCompileException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EngineCompileException(final Throwable cause) {
        super(cause.getMessage()
            + (cause.getCause() == null ? "" : " — " + cause.getCause().getMessage()), cause);
    }

    public EngineCompileException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
