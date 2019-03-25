# How to build project
This document helps people to compile and build the project in your maven and set your IDE.

## Build Project
**Because we are using Git submodule, we recommend don't use `GitHub` tag or release page to download source codes for compiling.**

### Build from GitHub
1. Prepare git, JDK8 and maven3
1. `git clone https://github.com/apache/incubator-skywalking.git`
1. `cd incubator-skywalking/`
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
- Compile backend and package
>  ./mvnw package -Pbackend,dist
- Compile UI and package
>  ./mvnw package -Pui,dist



## Setup your IntelliJ IDEA
1. Import the project as a maven project
1. Run `./mvnw compile -Dmaven.test.skip=true` to compile project and generate source codes. Because we use gRPC and protobuf.
1. Set **Generated Source Codes** folders.
    * `grpc-java` and `java` folders in **apm-protocol/apm-network/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/server-core/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/server-receiver-plugin/skywalking-istio-telemetry-receiver-plugin/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/server-receiver-plugin/envoy-metrics-receiver-plugin/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/exporter/target/generated-sources/protobuf**
    * `antlr4` folder in **oap-server/generate-tool-grammar/target/generated-sources**
    * `oal` folder in **oap-server/generated-analysis/target/generated-sources**
    
## Setup your Eclipse IDE
1. Import the project as a maven project
2. For supporting multiple source directories, you need to add the following configuration in `incubator-skywalking/pom.xml` file:
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
7. Run `./mvnw compile` complie collector-remote-grpc-provider and apm-protocol
8. Refresh project
