# How to build project
This document helps people to compile and build the project in your maven and set your IDE.

## Build Project
**Because we are using Git submodule, we recommend don't use `GitHub` tag or release page to download source codes for compiling.**

### Maven behind Proxy
If you need to execute build behind the proxy, edit the *.mvn/jvm.config* and put the follow properties:
```properties
-Dhttp.proxyHost=proxy_ip
-Dhttp.proxyPort=proxy_port
-Dhttps.proxyHost=proxy_ip
-Dhttps.proxyPort=proxy_port 
-Dhttp.proxyUser=username
-Dhttp.proxyPassword=password
```

### Build from GitHub
1. Prepare git, JDK8+ and Maven 3.6+
1. Clone project

    If you want to build a release from source codes, provide a `tag name` by using `git clone -b [tag_name] ...` while cloning.
    
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

### Build from Apache source code release
- What is `Apache source code release`?

For each official Apache release, there is a complete and independent source code tar, which is including all source codes. You could download it from [SkyWalking Apache download page](http://skywalking.apache.org/downloads/). No git related stuff required when compiling this. Just follow these steps.

1. Prepare JDK8+ and Maven 3.6+
1. Run `./mvnw clean package -DskipTests`
1. All packages are in `/dist`.(.tar.gz for Linux and .zip for Windows).

### Advanced compile
SkyWalking is a complex maven project, including many modules, which could cause long compiling time. 
If you just want to recompile part of the project, you have following options
- Compile agent and package
>  ./mvnw package -Pagent,dist

or

> make build.agent

If you intend to compile a single one plugin, such as in the dev stage, you could
>  cd plugin_module_dir & mvn clean package

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

Refer to [Build docker image](../../../docker) for more details.

## Setup your IntelliJ IDEA
**NOTICE**: If you clone the codes from GitHub, please make sure that you had finished step 1 to 3 in section **[Build from GitHub](#build-from-github)**, if you download the source codes from the official website of SkyWalking, please make sure that you had followed the steps in section **[Build from Apache source code release](#build-from-apache-source-code-release)**.

1. Import the project as a maven project
1. Run `./mvnw compile -Dmaven.test.skip=true` to compile project and generate source codes. Because we use gRPC and protobuf.
1. Set **Generated Source Codes** folders.
    * `grpc-java` and `java` folders in **apm-protocol/apm-network/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/server-core/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/server-receiver-plugin/receiver-proto/target/generated-sources/fbs**
    * `grpc-java` and `java` folders in **oap-server/server-receiver-plugin/receiver-proto/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/exporter/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/server-configuration/grpc-configuration-sync/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **oap-server/server-alarm-plugin/target/generated-sources/protobuf**
    * `antlr4` folder in **oap-server/oal-grammar/target/generated-sources**
