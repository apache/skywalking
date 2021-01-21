# Fix compiling on MacBook M1 chip
### Problem
- When compiling according to [How-to-build](../guides/How-to-build.md), The following problems will occur, causing the build to fail.
```
[ERROR] Failed to execute goal org.xolstice.maven.plugins:protobuf-maven-plugin:0.6.1:compile (grpc-build) on project apm-network: Unable to resolve artifact: Missing:
[ERROR] ----------
[ERROR] 1) com.google.protobuf:protoc:exe:osx-aarch_64:3.12.0
[ERROR]
[ERROR]   Try downloading the file manually from the project website.
[ERROR]
[ERROR]   Then, install it using the command:
[ERROR]       mvn install:install-file -DgroupId=com.google.protobuf -DartifactId=protoc -Dversion=3.12.0 -Dclassifier=osx-aarch_64 -Dpackaging=exe -Dfile=/path/to/file
[ERROR]
[ERROR]   Alternatively, if you host your own repository you can deploy the file there:
[ERROR]       mvn deploy:deploy-file -DgroupId=com.google.protobuf -DartifactId=protoc -Dversion=3.12.0 -Dclassifier=osx-aarch_64 -Dpackaging=exe -Dfile=/path/to/file -Durl=[url] -DrepositoryId=[id]
[ERROR]
[ERROR]   Path to dependency:
[ERROR]         1) org.apache.skywalking:apm-network:jar:8.4.0-SNAPSHOT
[ERROR]         2) com.google.protobuf:protoc:exe:osx-aarch_64:3.12.0
[ERROR]
[ERROR] ----------
[ERROR] 1 required artifact is missing.

```

### Reason
Because the dependent Protocol Buffers v3.14.0 does not have an osx-aarch_64 version, Protocol Buffers Releases link: https://github.com/protocolbuffers/protobuf/releases, fortunately, mac m1 is compatible with the osx-x86_64 version, before this version is available for download, you need to manually specify the osx-x86_64 version.

### Resolve
We can add -Dos.detected.classifier=osx-x86_64 after the original compilation parameters, for example: `./mvnw clean package -DskipTests -Dos.detected.classifier=osx-x86_64`, After specifying, compile and run normally.
