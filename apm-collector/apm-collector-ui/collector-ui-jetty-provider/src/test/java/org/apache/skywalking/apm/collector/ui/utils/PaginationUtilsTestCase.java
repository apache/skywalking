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

package org.apache.skywalking.apm.collector.ui.utils;

import org.apache.skywalking.apm.collector.storage.ui.common.Pagination;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author peng-yongsheng
 */
public class PaginationUtilsTestCase {

    @Test
    public void test() {
        Pagination pagination = new Pagination();
        pagination.setPageSize(10);
        pagination.setPageNum(1);

        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(pagination);
        Assert.assertEquals(0, page.getFrom());
        Assert.assertEquals(10, page.getLimit());

        pagination = new Pagination();
        pagination.setPageSize(10);

        page = PaginationUtils.INSTANCE.exchange(pagination);
        Assert.assertEquals(0, page.getFrom());
        Assert.assertEquals(10, page.getLimit());

        pagination = new Pagination();
        pagination.setPageSize(10);
        pagination.setPageNum(2);

        page = PaginationUtils.INSTANCE.exchange(pagination);
        Assert.assertEquals(10, page.getFrom());
        Assert.assertEquals(10, page.getLimit());
    }
}
