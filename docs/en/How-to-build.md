# How to build project
This document helps people to compile and build the project in your maven and IDE.

## Build in maven
1. Prepare JDK8 and maven3
1. Run `mvn clean package`
1. All packages are in `/packages`, which includes `skywalking-agent` folder, and two collector files(.tar.gz for Linux and .zip for Windows)

## Setup your IntelliJ IDEA
1. Import the project as a maven project
1. Run `mvn compile -Dmaven.test.skip=true` to compile project and generate source codes. Because we use gRPC and protobuf.
1. Set **Generated Source Codes** folders.
    * `grpc-java` and `java` folders in **apm-protocol/apm-network/target/generated-sources/protobuf**
    * `grpc-java` and `java` folders in **apm-collector/apm-collector-remote/apm-remote-grpc-provider/target/protobuf**