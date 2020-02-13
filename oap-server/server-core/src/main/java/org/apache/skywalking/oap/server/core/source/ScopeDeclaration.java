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

package org.apache.skywalking.oap.server.core.source;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord;

/**
 * ScopeDeclaration includes
 *
 * 1.Source entity used in OAL script, such as Service as a Scope could be used like this in the OAL script.
 *
 * service_resp_time = from(Service.latency).longAvg();
 *
 * 2. Manual source such as {@link Segment}
 * 
 * 3. None stream entity like {@link ProfileTaskRecord}.
 *
 * NOTICE, in OAL script, `disable` is for stream, rather than source, it doesn't require this annotation.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ScopeDeclaration {
    int id();

    String name();

    String catalog() default "";
}
