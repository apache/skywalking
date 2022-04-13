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

package org.apache.skywalking.oap.server.core.storage.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * BanyanDBGlobalIndex declares advanced global index, which are only available in BanyanDB.
 *
 * Global index should only be considered if a column value has a huge value candidates, but we will need a direct equal
 * query without timestamp.
 * The typical global index is designed for huge candidate of indexed values,
 * such as `trace ID` or `segment ID + span ID`
 *
 * Only work with {@link Column}
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface BanyanDBGlobalIndex {
    /**
     * The current column should be indexed through global index.
     *
     * @return empty array if only the current column should be indexed in global index. Or list of column names if this
     * global index includes multiple columns.
     */
    String[] extraFields();
}
