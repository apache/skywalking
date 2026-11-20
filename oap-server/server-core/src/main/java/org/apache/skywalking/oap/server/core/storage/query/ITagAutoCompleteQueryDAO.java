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

package org.apache.skywalking.oap.server.core.storage.query;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.library.module.Service;

public interface ITagAutoCompleteQueryDAO extends Service {
    /**
     * @param tagTypes the names of the tag types to read ({@link TagType#name()}), the tags of any of them;
     *                 not empty. Most of the time a query has only one tag type; only the logs' autocomplete
     *                 without a type reads two (LOG and OTLP_LOG).
     * @param limit    the most keys returned, across all the tag types
     * @param duration the time range
     * @return the distinct tag keys
     * @throws IOException when the storage cannot be read
     */
    Set<String> queryTagAutocompleteKeys(final List<String> tagTypes,
                                         final int limit,
                                         final Duration duration) throws IOException;

    /**
     * @param tagTypes the names of the tag types to read ({@link TagType#name()}), the tags of any of them;
     *                 not empty. Most of the time a query has only one tag type; only the logs' autocomplete
     *                 without a type reads two (LOG and OTLP_LOG).
     * @param tagKey   the key whose values are read
     * @param limit    the most values returned, across all the tag types
     * @param duration the time range
     * @return the distinct values of the key
     * @throws IOException when the storage cannot be read
     */
    Set<String> queryTagAutocompleteValues(final List<String> tagTypes,
                                           final String tagKey,
                                           final int limit,
                                           final Duration duration) throws IOException;
}
