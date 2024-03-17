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

package org.apache.skywalking.oap.server.webapp;

import static org.yaml.snakeyaml.env.EnvScalarConstructor.ENV_FORMAT;
import static org.yaml.snakeyaml.env.EnvScalarConstructor.ENV_TAG;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.env.EnvScalarConstructor;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.file.FileService;
import com.linecorp.armeria.server.file.HttpFile;
import com.linecorp.armeria.server.healthcheck.HealthCheckService;

@Slf4j
public class ApplicationStartUp {
    public static void main(String[] args) throws Exception {
        final Marker startedMarker = MarkerFactory.getMarker("Console");
        final Yaml yaml = new Yaml(
            new EnvScalarConstructor(
                new TypeDescription(Configuration.class),
                Collections.emptyList(),
                new LoaderOptions()));
        yaml.addImplicitResolver(ENV_TAG, ENV_FORMAT, "$");

        final Configuration configuration = yaml.loadAs(
            ApplicationStartUp.class.getResourceAsStream("/application.yml"),
            Configuration.class);

        final int port = configuration.port();
        final String[] oapServices = configuration.oapServices();

        final HttpService indexPage =
            HttpFile
                .of(ApplicationStartUp.class.getClassLoader(), "/public/index.html")
                .asService();
        final HttpService zipkinIndexPage =
            HttpFile
                .of(ApplicationStartUp.class.getClassLoader(), "/zipkin-lens/index.html")
                .asService();

        final ZipkinProxyService zipkin = new ZipkinProxyService(configuration.zipkinServices());

        Server
            .builder()
            .port(port, SessionProtocol.HTTP)
            .service("/graphql", new OapProxyService(oapServices))
            .service("/internal/l7check", HealthCheckService.of())
            .service("/zipkin/config.json", zipkin)
            .serviceUnder("/zipkin/api", zipkin)
            .serviceUnder("/zipkin",
                FileService.of(
                    ApplicationStartUp.class.getClassLoader(),
                    "/zipkin-lens")
                    .orElse(zipkinIndexPage))
            .serviceUnder("/",
                FileService.of(
                    ApplicationStartUp.class.getClassLoader(),
                    "/public")
                    .orElse(indexPage))
            .build()
            .start()
            .join();

        log.info(startedMarker, "SkyWalking Booster UI is now running. " +
            "OAP service at {} and Booster UI at http://localhost:{}",
            String.join(", ", oapServices), port);
    }
}
