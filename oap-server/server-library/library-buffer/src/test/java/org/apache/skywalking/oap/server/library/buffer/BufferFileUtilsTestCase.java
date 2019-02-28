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

package org.apache.skywalking.oap.server.library.buffer;

import java.util.*;
import org.junit.*;

/**
 * @author peng-yongsheng
 */
public class BufferFileUtilsTestCase {

    @Test
    public void testSort() {
        List<String> fileNames = new ArrayList<>();
        fileNames.add("data-1.sw");
        fileNames.add("data-3.sw");
        fileNames.add("data-2.sw");
        fileNames.add("data-8.sw");
        fileNames.add("data-5.sw");

        String[] files = fileNames.toArray(new String[0]);
        BufferFileUtils.sort(files);

        Assert.assertEquals("data-1.sw", files[0]);
        Assert.assertEquals("data-2.sw", files[1]);
        Assert.assertEquals("data-3.sw", files[2]);
        Assert.assertEquals("data-5.sw", files[3]);
        Assert.assertEquals("data-8.sw", files[4]);
    }
}
