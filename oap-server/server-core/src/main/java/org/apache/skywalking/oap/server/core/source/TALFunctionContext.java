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

package org.apache.skywalking.oap.server.core.source;

import org.slf4j.Logger;

/**
 * What a contributed TAL function receives when its first parameter is this type: the run it is called from.
 */
public interface TALFunctionContext {
    /**
     * @return the run's input, such as the native input's protobuf {@code SpanObject}
     */
    Object input();

    /**
     * @return the calling script's logger, {@code org.apache.skywalking.trace.analysis.script.<script name>}, under
     * which a function that logs writes
     */
    Logger scriptLogger();
}
