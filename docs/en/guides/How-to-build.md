# How to build a project
This document will help you compile and build a project in your maven and set your IDE.

## Building the Project
**Since we are using Git submodule, we do not recommend using the `GitHub` tag or release page to download source codes for compiling.**

### Maven behind the Proxy
If you need to execute build behind the proxy, edit the *.mvn/jvm.config* and set the follow properties:
```properties
-Dhttp.proxyHost=proxy_ip
-Dhttp.proxyPort=proxy_port
-Dhttps.proxyHost=proxy_ip
-Dhttps.proxyPort=proxy_port 
-Dhttp.proxyUser=username
-Dhttp.proxyPassword=password
```

### Building from GitHub
1. Prepare git, JDK8+, and Maven 3.6+.
1. Clone the project.

    If you want to build a release from source codes, set a `tag name` by using `git clone -b [tag_name] ...` while cloning.
    
    ```bash
    git clone --recurse-submodules https://github.com/apache/skywalking.git
    cd skywalking/
    
    OR
    
    git clone https://github.com/apache/skywalking.git
    cd skywalking/
    git submodule init
    git submodule update
    ```
   
1. Run `./mvnw clean package -DskipTests`
1. All packages are in `/dist` (.tar.gz for Linux and .zip for Windows).

### Building from Apache source code release
- What is the `Apache source code release`?

For each official Apache release, there is a complete and independent source code tar, which includes all source codes. You could download it from [SkyWalking Apache download page](http://skywalking.apache.org/downloads/). There is no requirement related to git when compiling this. Just follow these steps.

1. Prepare JDK8+ and Maven 3.6+.
1. Run `./mvnw clean package -DskipTests`.
1. All packages are in `/dist`.(.tar.gz for Linux and .zip for Windows).

### Advanced compiling
SkyWalking is a complex maven project that has many modules. Therefore, the time to compile may be a bit longer than usual.
If you just want to recompile part of the project, you have the following options:
- Compile agent and package
>  ./mvnw package -Pagent,dist

or

> make build.agent

If you intend to compile a single plugin, such as one in the dev stage, you could
>  cd plugin_module_dir & mvn clean package

- Compile backend and package
>  ./mvnw package -Pbackend,dist

or

> make build.backend

- Compile UI and package
>  ./mvnw package -Pui,dist

or

> make build.ui


### Building docker images
You can build docker images of `backend` and `ui` with `Makefile` located in root folder.

Refer to [Build docker image](../../../docker) for more details.

## Setting up your IntelliJ IDEA
**NOTE**: If you clone the codes from GitHub, please make sure that you have finished steps 1 to 3 in section **[Build from GitHub](#build-from-github)**. If you download the source codes from the official website of SkyWalking, please make sure that you have followed the steps in section **[Build from Apache source code release](#build-from-apache-source-code-release)**.

1. Import the project as a maven project.
1. Run `./mvnw compile -Dmaven.test.skip=true` to compile project and generate source codes. The reason is that we use gRPC and protobuf.
1. Set **Generated Source Codes** folders.
    * `grpc-java` and `java` folders in **apm-protocol/apm-network/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/server-core/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/server-receiver-plugin/receiver-proto/target/generated-sources/fbs**
    * `grpc-java` and `java` folders in **oap-server/server-receiver-plugin/receiver-proto/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/exporter/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/server-configuration/grpc-configuration-sync/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/server-alarm-plugin/target/generated-sources/protobuf**
    * `antlr4` folder in **oap-server/oal-grammar/target/generated-sources**
