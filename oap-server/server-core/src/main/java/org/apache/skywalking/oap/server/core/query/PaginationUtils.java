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

package org.apache.skywalking.oap.server.core.query;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.query.type.Pagination;

public enum PaginationUtils {
    INSTANCE;

    public Page exchange(Pagination paging) {
        int limit = paging.getPageSize();
        int from = paging.getPageSize() * ((paging.getPageNum() == 0 ? 1 : paging.getPageNum()) - 1);

        return new Page(from, limit);
    }

    @Data
    @RequiredArgsConstructor
    public static class Page {
        private final int from;
        private final int limit;
    }
}
