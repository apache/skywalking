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

package org.apache.skywalking.apm.testcase.guava.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class CaseServlet extends HttpServlet {

    Cache<String, Object> cache = CacheBuilder.newBuilder().softValues().build();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String key1 = "testKey1";
        String value1 = "testValue1";
        cache.put(key1, value1);
        cache.invalidate(key1);
        try {
            cache.get(key1, () -> value1);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        cache.getIfPresent(key1);
        Map<String, String> kvs = Maps.newHashMap();
        String key2 = "testKey2";
        String value2 = "testValue2";
        kvs.put(key2, value2);
        cache.putAll(kvs);
        List<String> testKeys = Lists.newArrayList(key1, key2);
        cache.getAllPresent(testKeys);
        cache.invalidateAll();
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
