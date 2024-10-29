/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

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

package org.apache.skywalking.oap.server.library.jfr.parser.convert;

import java.util.regex.Pattern;

public class Arguments {
    public String title = "Flame Graph";
    public String output;
    public String state;
    public Pattern include;
    public Pattern exclude;
    public int skip;
    public boolean reverse;
    public boolean cpu;
    public boolean wall;
    public boolean alloc;
    public boolean live;
    public boolean lock;
    public boolean threads;
    public boolean classify;
    public boolean total;
    public boolean lines;
    public boolean bci;
    public boolean simple;
    public boolean norm;
    public boolean dot;
    public long from;
    public long to;
}
