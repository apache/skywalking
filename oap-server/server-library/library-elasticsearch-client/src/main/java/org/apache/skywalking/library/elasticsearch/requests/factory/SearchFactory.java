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

package org.apache.skywalking.library.elasticsearch.requests.factory;

import com.linecorp.armeria.common.HttpRequest;
import org.apache.skywalking.library.elasticsearch.requests.search.Scroll;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchParams;

public interface SearchFactory {
    /**
     * Returns a request to search documents.
     */
    HttpRequest search(Search search, SearchParams params, String... index);

    /**
     * Returns a request to retrieve the next batch of results for a scrolling search.
     */
    HttpRequest scroll(Scroll scroll);

    /**
     * Returns a request to delete the scroll context.
     */
    HttpRequest deleteScrollContext(String scrollId);

    /**
     * Returns a request to search documents.
     */
    default HttpRequest search(Search search, String... index) {
        return search(search, null, index);
    }
}
