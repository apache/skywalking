# How to build project
This document helps people to compile and build the project in your maven and set your IDE.

## Build Project
**Because we are using Git submodule, we recommend don't use `GitHub` tag or release page to download source codes for compiling.**

### Build from GitHub
1. Prepare git, JDK8 and maven3
1. `git clone https://github.com/apache/skywalking.git`
1. `cd skywalking/`
1. Switch to the tag by using `git checkout [tagname]` (Optional, switch if want to build a release from source codes)
1. `git submodule init`
1. `git submodule update`
1. Run `./mvnw clean package -DskipTests`
1. All packages are in `/dist` (.tar.gz for Linux and .zip for Windows).

### Build from Apache source code release
- What is `Apache source code release`?

For each official Apache release, there is a complete and independent source code tar, which is including all source codes. You could download it from [SkyWalking Apache download page](http://skywalking.apache.org/downloads/). No git related stuff required when compiling this. Just follow these steps.

1. Prepare JDK8 and maven3
1. Run `./mvnw clean package -DskipTests`
1. All packages are in `/dist`.(.tar.gz for Linux and .zip for Windows).

### Advanced compile
SkyWalking is a complex maven project, including many modules, which could cause long compiling time. 
If you just want to recompile part of the project, you have following options
- Compile agent and package
>  ./mvnw package -Pagent,dist

or

> make build.agent

- Compile backend and package
>  ./mvnw package -Pbackend,dist

or

> make build.backend

- Compile UI and package
>  ./mvnw package -Pui,dist

or

> make build.ui


### Build docker images
We can build docker images of `backend` and `ui` with `Makefile` located in root folder.

- Build all docker images
> make docker.all

- Build oap server docker image
> make docker.oap

- Build ui docker image
> make docker.ui

`HUB` and `TAG` variables ares used to setup `REPOSITORY` and `TAG` of a docker image. To get
a oap image with name `bar/oap:foo`, run the following command
> HUB=bar TAG=foo make docker.oap

## Setup your IntelliJ IDEA
1. Import the project as a maven project
1. Run `./mvnw compile -Dmaven.test.skip=true` to compile project and generate source codes. Because we use gRPC and protobuf.
1. Set **Generated Source Codes** folders.
    * `grpc-java` and `java` folders in **apm-protocol/apm-network/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/server-core/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/server-receiver-plugin/receiver-proto/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/exporter/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/server-configuration/grpc-configuration-sync/target/generated-sources/protobuf**
    * `antlr4` folder in **oap-server/generate-tool-grammar/target/generated-sources**
    * `oal` folder in **oap-server/generated-analysis/target/generated-sources**
    
## Setup your Eclipse IDE
1. Import the project as a maven project
2. For supporting multiple source directories, you need to add the following configuration in `skywalking/pom.xml` file:
```
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>build-helper-maven-plugin</artifactId>
    <version>1.8</version>
    <executions>
        <execution>
            <id>add-source</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>add-source</goal>
            </goals>
            <configuration>
                <sources>
                    <source>src/java/main</source>
                    <source>apm-protocol/apm-network/target/generated-sources/protobuf</source>
                    <source>apm-collector/apm-collector-remote/collector-remote-grpc-provider/target/generated-sources/protobuf</source>
               </sources>
            </configuration>
        </execution>
    </executions>
</plugin>
```
3. Add the following configuration under to let eclipse's M2e plug-in supports execution's solution configuration
```
<pluginManagement>
    <plugins>
    <!--This plugin's configuration is used to store Eclipse m2e settings 
    only. It has no influence on the Maven build itself. -->
        <plugin>
            <groupId>org.eclipse.m2e</groupId>
            <artifactId>lifecycle-mapping</artifactId>
            <version>1.0.0</version>
            <configuration>
                <lifecycleMappingMetadata>
                    <pluginExecutions>
                        <pluginExecution>
                            <pluginExecutionFilter>
                                <groupId>org.codehaus.mojo</groupId>
                                <artifactId>build-helper-maven-plugin</artifactId>
                                <versionRange>[1.8,)</versionRange>
                                <goals>
                                    <goal>add-source</goal>
                                </goals>
                            </pluginExecutionFilter>
                        </pluginExecution>
                    </pluginExecutions>
                </lifecycleMappingMetadata>
            </configuration>
        </plugin>
    </plugins>
</pluginManagement>
```
4. Adding Google guava dependency to apm-collector-remote/collector-remote-grpc-provider/pom.xml files
```
<dependency>
   <groupId>com.google.guava</groupId>
   <artifactId>guava</artifactId>
   <version>24.0-jre</version>
</dependency>
```
5. Run `./mvnw compile -Dmaven.test.skip=true`
6. Run `maven update`. Must remove the clean projects item before maven update(This will be clear the proto conversion Java file generated by the complie)
7. Run `./mvnw compile` compile collector-remote-grpc-provider and apm-protocol
8. Refresh project

## Write necessary tests and run them

After setting up the environment and writing your codes, to make it more easily accepted by SkyWalking project, you'll
need to run the tests locally to verify that your codes don't break any existed features,
and write some unit test (UT) codes to verify that the new codes work well, preventing them being broke by future contributors.
If the new codes involve other components or libraries, you're also supposed to write integration tests (IT).

SkyWalking leverages plugin `maven-surefire-plugin` to run the UTs while using `maven-failsafe-plugin`
to run the ITs, `maven-surefire-plugin` will exclude ITs (whose class name starts with `IT`)
and leave them for `maven-failsafe-plugin` to run, which is bound to the `verify` goal, `CI-with-IT` profile.
Therefore, to run the UTs, try `./mvnw clean test`, this will only run the UTs, not including ITs.

If you want to run the ITs please activate the `CI-with-IT` profile
as well as the the profiles of the modules whose ITs you want to run.
e.g. if you want to run the ITs in `oap-server`, try `./mvnw -Pbackend,CI-with-IT clean verify`,
and if you'd like to run all the ITs, simple run `./mvnw -Pall,CI-with-IT clean verify`.

Please be advised that if you're writing integration tests, name it with the pattern `IT*` to make them only run in `CI-with-IT` profile.
