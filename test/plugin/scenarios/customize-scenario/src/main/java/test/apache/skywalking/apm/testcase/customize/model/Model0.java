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

package test.apache.skywalking.apm.testcase.customize.model;

import java.util.List;
import java.util.Map;

public class Model0 {

    public Model0(String id, int num, Model1 model1, Map m, List l, Object[] os) {
        this.id = id;
        this.num = num;
        this.model1 = model1;
        this.m = m;
        this.l = l;
        this.os = os;
    }

    private String id;
    private int num;
    private Model1 model1;
    private Map m;
    private List l;
    private Object[] os;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public Model1 getModel1() {
        return model1;
    }

    public void setModel1(Model1 model1) {
        this.model1 = model1;
    }

    public Map getM() {
        return m;
    }

    public void setM(Map m) {
        this.m = m;
    }

    public List getL() {
        return l;
    }

    public void setL(List l) {
        this.l = l;
    }

    public Object[] getOs() {
        return os;
    }

    public void setOs(Object[] os) {
        this.os = os;
    }
}
