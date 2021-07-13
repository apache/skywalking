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

package test.apache.skywalking.apm.testcase.xxljob.job;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.IJobHandler;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.skywalking.apm.testcase.xxljob.Utils;

@Slf4j
public class BeanJob extends IJobHandler {

    @Override
    public ReturnT<String> execute(String param) throws Exception {

        log.info("BeanJobHandler execute. param: {}", param);
        
        Request request = new Request.Builder().url("http://localhost:8080/xxl-job-2.x-scenario/case/simpleJob").build();
        Response response = Utils.OK_CLIENT.newCall(request).execute();
        response.body().close();

        return new ReturnT<String>("Success");
    }
}
