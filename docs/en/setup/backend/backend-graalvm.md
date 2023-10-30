# GraalVM native-image for SkyWalking (Experimental)

## Native-Image
Native Image is a technology to ahead-of-time compile Java code to a standalone executable, called a native image. 
This executable includes the application classes, classes from its dependencies, runtime library classes, 
and statically linked native code from JDK. It does not run on the Java VM, but includes necessary components like memory management, thread scheduling, and so on from a different runtime system, called “Substrate VM”. 
Substrate VM is the name for the runtime components (like the deoptimizer, garbage collector, thread scheduling etc.).
The resulting program has faster startup time and lower runtime memory overhead compared to a JVM.

SkyWalking currently offers support for OAP servers running as native-image. However, please note that the OAP started in this manner does not have the same functionality as the regular OAP, and some features are not yet supported.

## Prerequisites
Before proceeding with the compilation process, it's crucial to have the GraalVM JDK installed on your machine as the native-image compilation is dependent on it.

### Installing GraalVM JDK
Refer to [GraalVM's official download page](https://www.graalvm.org/downloads/) for downloading and configuring GraalVM JDK. A convenient method is utilizing SDKMAN, which allows you to download and install GraalVM JDK with a single command:

```shell
sdk install java 17.0.9-graal
```
Upon executing the above command, SDKMAN will automatically download and install the specified version of GraalVM JDK, preparing your environment for the subsequent native-image compilation process.

### Installing Native Image
In some download methods, the Native Image component is not automatically installed and needs to be downloaded separately. Users can download this component by executing the following command:

```shell
gu install native-image
```

This command utilizes the GraalVM Updater (gu) to install the Native Image component, ensuring the environment is properly set up for native-image compilation.

## Compile Guide
Notice: If you are not familiar with the compilation process, please read [How-to-build](../../guides/How-to-build.md) first.

The native-image compilation is not enabled by default. To enable it, we need to activate `native` profile during compilation, such as:

```shell

./mvnw -Pbackend,native clean package -Dmaven.test.skip

```

Then, 2 packages are in `graal/dist`,
As this is an experimental feature, a package named `apache-skywalking-apm-native-pre-bin.tar.gz` is a tarball for GraalVMization friendly, including original classes to be compiled by GraalVM.
The package named `apache-skywalking-apm-native-bin.tar.gz` includes the final compiled native binary, relative configuration files, and booting shells. Read `Package Structure` doc for more details.

## Package Structure

SkyWalking’s native-image distribution package consists of the following parts:

* bin/cmd scripts: Located in the /bin folder. Includes startup Linux shell and Windows cmd scripts for the backend server.

* Backend config: Located in the /config folder. Includes settings files of the backend. Running native-image does not require additional configuration, so you can refer to [backend-setup](backend-setup.md) to learn how to configure it.

* Native image executable: Located in /image folder. It can be run directly, but it is not recommended to do so, as the absence of some environment variables can lead to startup failure. 

## How To Use
By executing following:

```shell
./bin/oapService-native.sh 
```
we can successfully start SkyWalking-oap.

## Differences and TODO
With native-image, some features are not yet supported.

1. [LAL](../../concepts-and-designs/lal.md), [MAL](../../concepts-and-designs/mal.md), and some other features related to them are not supported at the moment.
2. The [OAL](../../concepts-and-designs/oal.md) files are used in the compiling stage, which means that users would not see these files inside the native image package, and can't change it. Consider recompiling and packaging from the source codes including your OAL file changes.

## Current Limitations
Native-image supports reflection and other dynamic features through some JSON-formatted configuration files. SkyWalking currently provides a set of configuration files for basic support. You can find them [here](../../../../graal/graal-server-starter/src/main/resources/META-INF/native-image/main).

For now, these configuration files do not support all runtime environments (but will be fully supported in the future). Therefore, in other environments, users may need to generate the configuration files required by native-image on their own.

SkyWalking uses [native build tools](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html) to generate native-image. Additionally, GraalVM provides an agent to assist in generating configuration files. Therefore, users can generate the required configuration files by referring to [native build tools agent guide](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html#agent-support).

