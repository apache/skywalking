# GraalVM native-image for SkyWalking (Experimental)

## Native-Image
Native Image is a technology to ahead-of-time compile Java code to a standalone executable, called a native image. 
This executable includes the application classes, classes from its dependencies, runtime library classes, 
and statically linked native code from JDK. It does not run on the Java VM, but includes necessary components like memory management, thread scheduling, and so on from a different runtime system, called “Substrate VM”. 
Substrate VM is the name for the runtime components (like the deoptimizer, garbage collector, thread scheduling etc.).
The resulting program has faster startup time and lower runtime memory overhead compared to a JVM.

SkyWalking currently offers support for OAP servers running as native-image. However, please note that the OAP started in this manner does not have the same functionality as the regular OAP, and some features are not yet supported.

## Compile Guide
Notice: If you are not familiar with the compilation process, please read [How-to-build](https://skywalking.apache.org/docs/main/next/en/guides/how-to-build/) first.

The native-image compilation is not enabled by default. To enable it, we need to activate `native` profile during compilation, such as:

```shell

./mvnw clean install -Dmaven.test.skip

./mvnw -Pnative clean package -Dmaven.test.skip

```

Then, 2 packages are in `distribution/graal/dist`, The package named `apache-skywalking-apm-native-pre-bin.tar.gz` is unnecessary for most users. It is just an intermediate product for generating the final native-image program, used for testing purposes.
The real outcome is the package named `apache-skywalking-apm-native-bin.tar.gz`, and followings are the introduction to its package structure.

## Package Structure

SkyWalking’s native-image distribution package consists of the following parts:

* bin/cmd scripts: Located in the /bin folder. Includes startup Linux shell and Windows cmd scripts for the backend server.

* Backend config: Located in the /config folder. Includes settings files of the backend. Running native-image does not require additional configuration, so you can refer to [backend-setup](https://skywalking.apache.org/docs/main/next/en/setup/backend/backend-setup/) to learn how to configure it.

* Native image executable: Located in /image folder. It can be run directly, but it is not recommended to do so, as the absence of some environment variables can lead to startup failure. 

## How To Use
By executing following:

```shell
./bin/oapService-native.sh 
```
we can successfully start SkyWalking-oap.

## Differences and TODO
With native-image, some features are not yet supported.

1. [LAL](https://skywalking.apache.org/docs/main/next/en/concepts-and-designs/lal/), [MAL](https://skywalking.apache.org/docs/main/next/en/concepts-and-designs/mal/), and some other features related to them are not supported at the moment.
2. The [OAL](https://skywalking.apache.org/docs/main/next/en/concepts-and-designs/oal/) files are used in the compiling stage, which means that users would not see these files inside the native image package, and can't change it. Consider recompiling and packaging from the source codes including your OAL file changes.