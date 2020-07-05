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

package org.apache.skywalking.apm.plugin.customize.util;

import org.apache.skywalking.apm.agent.core.util.CustomizeExpression;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomizeExpressionTest {

    @Test
    public void testExpression() {
        Object[] allArguments = init();
        Map<String, Object> context = CustomizeExpression.evaluationContext(allArguments);
        Assert.assertTrue("String_test".equals(CustomizeExpression.parseExpression("arg[0]", context)));
        Assert.assertTrue("1024".equals(CustomizeExpression.parseExpression("arg[1]", context)));
        Assert.assertTrue("v2_1".equals(CustomizeExpression.parseExpression("arg[2].['k2_1']", context)));
        Assert.assertTrue("test1".equals(CustomizeExpression.parseExpression("arg[3].[1]", context)));
        Assert.assertTrue("null".equals(CustomizeExpression.parseExpression("arg[3].[100]", context)));
        Assert.assertTrue("100".equals(CustomizeExpression.parseExpression("arg[4].id", context)));
        Assert.assertTrue("sw".equals(CustomizeExpression.parseExpression("arg[4].getName()", context)));
        Assert.assertTrue("ext_v_1".equals(CustomizeExpression.parseExpression("arg[4].ext.['ext_k_1']", context)));
        Assert.assertTrue("uuid".equals(CustomizeExpression.parseExpression("arg[5].uuid", context)));
        Assert.assertTrue("c".equals(CustomizeExpression.parseExpression("arg[5].orderIds.[0]", context)));
        Assert.assertTrue("2".equals(CustomizeExpression.parseExpression("arg[5].ids.[2]", context)));
        Assert.assertTrue("3".equals(CustomizeExpression.parseExpression("arg[5].ids.[1]", context)));
        Assert.assertTrue("open_id".equals(CustomizeExpression.parseExpression("arg[5].openId", context)));
        Assert.assertTrue("ext_v_2".equals(CustomizeExpression.parseExpression("arg[5].user.ext.['ext_k_2']", context)));
    }

    private static Object[] init() {
        Object[] allArguments = new Object[6];
        allArguments[0] = "String_test";
        allArguments[1] = 1024;
        Map m0 = new HashMap();
        m0.put("k2_1", "v2_1");
        allArguments[2] = m0;
        List l0 = new ArrayList();
        l0.add("test0");
        l0.add("test1");
        allArguments[3] = l0;
        Map m1 = new HashMap();
        m1.put("ext_k_1", "ext_v_1");
        allArguments[4] = new User(100, "sw", m1);
        Map m2 = new HashMap();
        m2.put("ext_k_2", "ext_v_2");
        User user2 = new User(101, "sw0", m2);
        List l1 = new ArrayList();
        l1.add("c");
        Order order = new Order(999, "uuid", l1, user2, "open_id", new Object[] {
            0,
            3,
            "2"
        });
        allArguments[5] = order;
        return allArguments;
    }

    static class Order {
        public Order(int id, String uuid, List orderIds, User user, String openId, Object[] ids) {
            this.id = id;
            this.uuid = uuid;
            this.orderIds = orderIds;
            this.user = user;
            this.openId = openId;
            this.ids = ids;
        }

        private int id;
        private String uuid;
        private List orderIds;
        private User user;
        public String openId;
        private Object[] ids;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public List getOrderIds() {
            return orderIds;
        }

        public void setOrderIds(List orderIds) {
            this.orderIds = orderIds;
        }

        public User getUser() {
            return user;
        }

        public void setUser(User user) {
            this.user = user;
        }

        public Object[] getIds() {
            return ids;
        }

        public void setIds(Object[] ids) {
            this.ids = ids;
        }
    }

    static class User {

        public User(int id, String name, Map ext) {
            this.id = id;
            this.name = name;
            this.ext = ext;
        }

        private int id;
        private String name;
        private Map ext;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map getExt() {
            return ext;
        }

        public void setExt(Map ext) {
            this.ext = ext;
        }
    }
}
