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

package org.apache.skywalking.apm.testcase.ehcache.v2;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

public class CaseServlet extends HttpServlet {

    CacheManager cacheManager = CacheManager.create(CaseServlet.class.getResource("/cache.xml"));

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Cache cache = cacheManager.getCache("testCache");

        String objectKey = "dataKey";

        Element el = new Element(objectKey, "2");

        // EhcacheOperateElementInterceptor
        cache.put(el);

        // EhcacheOperateObjectInterceptor
        cache.get(objectKey);

        // EhcacheOperateAllInterceptor
        cache.putAll(Arrays.asList(new Element[] {el}));

        // EhcacheLockInterceptor
        try {
            boolean success = cache.tryReadLockOnKey(objectKey, 300);
        } catch (InterruptedException e) {
        } finally {
            cache.releaseReadLockOnKey(objectKey);
        }

        // EhcacheCacheNameInterceptor
        cacheManager.addCacheIfAbsent("testCache2");

        Cache cloneCache = cacheManager.getCache("testCache2");

        // EhcacheOperateElementInterceptor
        cloneCache.put(el);

        PrintWriter printWriter = resp.getWriter();
        printWriter.write("success");
        printWriter.flush();
        printWriter.close();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

}
