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
 */

package org.apache.skywalking.apm.testcase.armeria;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import java.util.concurrent.CompletableFuture;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class ServerApplication {
    private static final String SUCCESS = "Success";

    @PostConstruct
    public void init() {
        ServerBuilder sb = new ServerBuilder().http(8085)
                                              .service("/healthCheck", (ctx, res) -> HttpResponse.of(SUCCESS))
                                              .service("/greet/{name}", (ctx, res) -> HttpResponse.of("Hello %s~", ctx.pathParam("name")));

        Server server = sb.build();
        CompletableFuture<Void> future = server.start();
        future.join();
    }
}
