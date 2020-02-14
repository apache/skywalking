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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.standardization;

import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.StandardBuilder;

/**
 * The implementation has details to do String to ID(integer) transformation.
 */
public interface IdExchanger<T extends StandardBuilder> {
    /**
     * Register all required fields in the builder to get the assigned IDs.
     *
     * @param standardBuilder object includes unregistered data.
     * @param serviceId       service id of this builder.
     * @return true if all register completed. NOTICE, because the register is in async mode, mostly because this is a
     * distributed register mechanism, check {@link org.apache.skywalking.oap.server.core.register.worker.InventoryStreamProcessor},
     * the required ID could be unreachable as the register is still in processing. But in the production environment,
     * besides the moments of the SkyWalking just being setup or new service/instance/endpoint online, all the registers
     * should have finished back to when they are accessed at the first time. This register could process very fast.
     */
    boolean exchange(T standardBuilder, int serviceId);
}
