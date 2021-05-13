/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.core.query;

import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.query.type.ErrorCategory;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

@RequiredArgsConstructor
public class BrowserLogQueryService implements Service {
    private final ModuleManager moduleManager;
    private IBrowserLogQueryDAO browserLogQueryDAO;

    private IBrowserLogQueryDAO getBrowserLogQueryDAO() {
        return Optional.ofNullable(browserLogQueryDAO).orElseGet(() -> {
            browserLogQueryDAO = moduleManager.find(StorageModule.NAME)
                                              .provider()
                                              .getService(IBrowserLogQueryDAO.class);
            return browserLogQueryDAO;
        });
    }

    public BrowserErrorLogs queryBrowserErrorLogs(final String serviceId,
                                                  final String serviceVersionId,
                                                  final String pagePathId,
                                                  final String pagePath,
                                                  final ErrorCategory category,
                                                  final long startSecondTB,
                                                  final long endSecondTB,
                                                  final Pagination paging) throws IOException {
        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(paging);
        BrowserErrorCategory errorCategory = Optional.ofNullable(category)
                                                     .filter(c -> c != ErrorCategory.ALL) // ErrorCategory.All stands for query all.
                                                     .map(c -> BrowserErrorCategory.valueOf(c.name()))
                                                     .orElse(null);

        return getBrowserLogQueryDAO().queryBrowserErrorLogs(
            serviceId, serviceVersionId, pagePathId, pagePath, errorCategory, startSecondTB, endSecondTB,
            page.getLimit(),
            page.getFrom()
        );
    }
}
