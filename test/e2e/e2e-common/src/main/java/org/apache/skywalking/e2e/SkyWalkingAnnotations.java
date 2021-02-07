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

package org.apache.skywalking.e2e;

import com.google.common.base.Strings;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.annotation.ContainerHost;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.ContainerPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.annotation.DockerContainer;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.docker.DockerComposeFile;
import org.apache.skywalking.e2e.logging.ContainerLogger;
import org.apache.skywalking.e2e.utils.Envs;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

import static java.util.stream.Collectors.joining;
import static org.apache.skywalking.e2e.utils.Yamls.load;

/**
 * {@link #init(Object) SkyWalkingAnnotations.init(this)} initializes fields annotated with SkyWalking annotations. You
 * don't usually need to call this method to initialize if the {@link SkyWalkingExtension} is already used, which does
 * the work for you automatically:
 *
 * <pre>{@code
 * @ExtendWith(SkyWalkingExtension.class)
 * @TestInstance(TestInstance.Lifecycle.PER_CLASS)
 * public class SomeTest {
 *     @DockerCompose("docker-compose.yml")
 *     private DockerComposeContainer justForSideEffects;
 *
 *     @ContainerHostAndPort(name = "service-name1-in-docker-compose.yml", port = 8080)
 *     private HostAndPort someService1HostPort;
 *
 *     @ContainerHostAndPort(name = "service-name2-in-docker-compose.yml", port = 9090)
 *     private HostAndPort someService2HostPort;
 * }
 * }</pre>
 *
 * but if you don't use the extension for some reasons (which is rare), here is an example:
 *
 * <pre>{@code
 * public class SomeTest {
 *     @DockerCompose("docker/simple/docker-compose.yml")
 *     private DockerComposeContainer justForSideEffects;
 *
 *     @ContainerHostAndPort(name = "service-name1-in-docker-compose.yml", port = 8080)
 *     private HostAndPort someService1HostPort;
 *
 *     @ContainerHostAndPort(name = "service-name2-in-docker-compose.yml", port = 9090)
 *     private HostAndPort someService2HostPort;
 *
 *     @BeforeAll
 *     public void setUp() throws Exception {
 *         SkyWalkingAnnotations.init(this);
 *     }
 * }
 * }</pre>
 */
@Slf4j
public final class SkyWalkingAnnotations {
    private static final boolean IS_CI = !Strings.isNullOrEmpty(System.getenv("GITHUB_RUN_ID"));
    private static final String IDENTIFIER =
        !Strings.isNullOrEmpty(System.getenv("GITHUB_RUN_ID"))
            ? System.getenv("GITHUB_RUN_ID") : "skywalking-e2e-";
    private static final String LOG_DIR_ENV =
        !Strings.isNullOrEmpty(System.getenv("GITHUB_WORKSPACE"))
            ? (System.getenv("GITHUB_WORKSPACE") + "/logs") : "/tmp/skywalking/logs";
    private static final Path LOG_DIR = Paths.get(LOG_DIR_ENV);

    private static final List<String> REMOTE_SERVICE_NAMES = new LinkedList<>();
    private static final int REMOTE_DEBUG_PORT = 5005;

    static {
        LOGGER.info("IDENTIFIER={}", IDENTIFIER);
        LOGGER.info("LOG_DIR={}", LOG_DIR);
    }

    public static void init(final Object testClass) throws Exception {
        Objects.requireNonNull(testClass, "testClass");

        final DockerComposeContainer<?> compose = initDockerComposeField(testClass).orElseThrow(RuntimeException::new);

        compose.start();

        initHostAndPort(testClass, compose);

        initDockerContainers(testClass, compose);
    }

    /**
     * Destroy the containers started by the docker compose in the given test class, this should be typically called in
     * the corresponding {@code @AfterAll} or {@code @AfterEach} method.
     *
     * @param testClass in which the containers should be destroyed
     */
    public static void destroy(final Object testClass) {
        Stream.of(testClass.getClass().getDeclaredFields())
              .filter(SkyWalkingAnnotations::isAnnotatedWithDockerCompose)
              .findFirst()
              .ifPresent(field -> {
                  try {
                      field.setAccessible(true);
                      ((DockerComposeContainer<?>) field.get(testClass)).stop();
                  } catch (IllegalAccessException e) {
                      throw new RuntimeException(e);
                  }
              });
    }

    private static void initHostAndPort(final Object testClass,
                                        final DockerComposeContainer<?> compose) throws Exception {
        final Field[] fields = testClass.getClass().getDeclaredFields();
        for (final Field field : fields) {
            if (field.isAnnotationPresent(ContainerHost.class) && field.isAnnotationPresent(ContainerPort.class)) {
                throw new RuntimeException(
                    "field cannot be annotated with both ContainerHost and ContainerPort: " + field.getName()
                );
            }
            if (field.isAnnotationPresent(ContainerHost.class)) {
                final ContainerHost host = field.getAnnotation(ContainerHost.class);
                field.setAccessible(true);
                field.set(testClass, compose.getServiceHost(host.name(), host.port()));
            }
            if (field.isAnnotationPresent(ContainerPort.class)) {
                final ContainerPort host = field.getAnnotation(ContainerPort.class);
                field.setAccessible(true);
                field.set(testClass, compose.getServicePort(host.name(), host.port()));
            }
            if (field.isAnnotationPresent(ContainerHostAndPort.class)) {
                final ContainerHostAndPort hostAndPort = field.getAnnotation(ContainerHostAndPort.class);
                final String host = compose.getServiceHost(hostAndPort.name(), hostAndPort.port());
                final int port = compose.getServicePort(hostAndPort.name(), hostAndPort.port());

                field.setAccessible(true);
                field.set(testClass, HostAndPort.builder().host(host).port(port).build());
            }
        }
        if (!IS_CI) {
            File portFile = new File("remote_real_port");
            portFile.createNewFile();
            FileWriter fileWriter = new FileWriter(portFile.getName());
            for (String service : REMOTE_SERVICE_NAMES) {
                fileWriter.write(String.format("%s-%s:%s\n", service, compose.getServiceHost(service, REMOTE_DEBUG_PORT),
                        compose.getServicePort(service, REMOTE_DEBUG_PORT)));
            }
            fileWriter.flush();
            fileWriter.close();
        }
    }

    private static Optional<DockerComposeContainer<?>> initDockerComposeField(final Object testClass) throws Exception {
        final Field[] fields = testClass.getClass().getDeclaredFields();
        final List<Field> dockerComposeFields = Stream.of(fields)
                                                      .filter(SkyWalkingAnnotations::isAnnotatedWithDockerCompose)
                                                      .collect(Collectors.toList());

        if (dockerComposeFields.isEmpty()) {
            return Optional.empty();
        }

        if (dockerComposeFields.size() > 1) {
            throw new RuntimeException("can only have one field annotated with @DockerCompose");
        }

        final Field dockerComposeField = dockerComposeFields.get(0);
        final DockerCompose dockerCompose = dockerComposeField.getAnnotation(DockerCompose.class);
        final List<File> files = Stream.of(dockerCompose.value()).map(Envs::resolve)
                                       .map(File::new).collect(Collectors.toList());
        final DockerComposeContainer<?> compose = new DockerComposeContainer<>(IDENTIFIER, files);

        if (!IS_CI) {
            List<String> filePathList =  files.stream().map(File::getAbsolutePath).collect(Collectors.toList());
            try {
                LOGGER.info("Parse files:{}", filePathList.stream().collect(joining(" ", " ", "")));
                DockerComposeFile dockerComposeFile = DockerComposeFile.getAllConfigInfo(filePathList);

                dockerComposeFile.getServices().forEach((service, ignored) -> {
                    if (dockerComposeFile.isExposedPort(service, REMOTE_DEBUG_PORT)) {
                        REMOTE_SERVICE_NAMES.add(service);
                        compose.withExposedService(service, REMOTE_DEBUG_PORT, Wait.forListeningPort());
                    }
                });
            } catch (IOException | InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        for (final Field field : fields) {
            if (field.isAnnotationPresent(ContainerHost.class) && field.isAnnotationPresent(ContainerPort.class)) {
                throw new RuntimeException(
                    "field cannot be annotated with both ContainerHost and ContainerPort: " + field.getName()
                );
            }
            final WaitStrategy waitStrategy = Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5));
            if (field.isAnnotationPresent(ContainerHost.class)) {
                final ContainerHost host = field.getAnnotation(ContainerHost.class);
                compose.withExposedService(host.name(), host.port(), waitStrategy);
            }
            if (field.isAnnotationPresent(ContainerPort.class)) {
                final ContainerPort port = field.getAnnotation(ContainerPort.class);
                compose.withExposedService(port.name(), port.port(), waitStrategy);
            }
            if (field.isAnnotationPresent(ContainerHostAndPort.class)) {
                final ContainerHostAndPort hostAndPort = field.getAnnotation(ContainerHostAndPort.class);
                compose.withExposedService(hostAndPort.name(), hostAndPort.port(), waitStrategy);
            }
        }

        compose.withPull(true)
               .withLocalCompose(true)
               .withTailChildContainers(true)
               .withRemoveImages(
                   IS_CI ? DockerComposeContainer.RemoveImages.ALL : DockerComposeContainer.RemoveImages.LOCAL
               );

        if (IS_CI) {
            initLoggers(files, compose);
        }

        dockerComposeField.setAccessible(true);
        dockerComposeField.set(testClass, compose);

        return Optional.of(compose);
    }

    private static void initLoggers(final List<File> files, final DockerComposeContainer<?> compose) {
        files.forEach(file -> {
            try {
                load(file).as(DockerComposeFile.class).getServices().forEach(
                    (service, ignored) -> compose.withLogConsumer(
                        service, new Slf4jLogConsumer(new ContainerLogger(LOG_DIR, service + ".log"))
                    )
                );
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void initDockerContainers(final Object testClass,
                                             final DockerComposeContainer<?> compose) throws Exception {
        final List<Field> containerFields = Stream.of(testClass.getClass().getDeclaredFields())
                                                  .filter(SkyWalkingAnnotations::isAnnotatedWithDockerContainer)
                                                  .collect(Collectors.toList());
        if (containerFields.isEmpty()) {
            return;
        }

        final Field serviceMap = DockerComposeContainer.class.getDeclaredField("serviceInstanceMap");
        serviceMap.setAccessible(true);
        final Map<String, ContainerState> serviceInstanceMap = (Map<String, ContainerState>) serviceMap.get(compose);

        for (final Field containerField : containerFields) {
            if (containerField.getType() != ContainerState.class) {
                throw new IllegalArgumentException(
                    "@DockerContainer can only be annotated on fields of type " + ContainerState.class.getName()
                        + " but was " + containerField.getType() + "; field \"" + containerField.getName() + "\""
                );
            }
            final DockerContainer dockerContainer = containerField.getAnnotation(DockerContainer.class);
            final String serviceName = dockerContainer.value();
            final Optional<ContainerState> container =
                serviceInstanceMap.entrySet()
                                  .stream()
                                  .filter(e -> e.getKey().startsWith(serviceName + "_"))
                                  .findFirst()
                                  .map(Map.Entry::getValue);
            containerField.setAccessible(true);
            containerField.set(
                testClass,
                container.orElseThrow(
                    () -> new NoSuchElementException("cannot find container with name " + serviceName)
                )
            );
        }
    }

    private static boolean isAnnotatedWithDockerCompose(final Field field) {
        return field.isAnnotationPresent(DockerCompose.class);
    }

    private static boolean isAnnotatedWithDockerContainer(final Field field) {
        return field.isAnnotationPresent(DockerContainer.class);
    }
}
