# How to use SkyWalking with GraalVM native-image (Experimental)

## Native-Image
Native Image is a technology to ahead-of-time compile Java code to a standalone executable, called a native image. 
This executable includes the application classes, classes from its dependencies, runtime library classes, 
and statically linked native code from JDK. It does not run on the Java VM, but includes necessary components like memory management, thread scheduling, and so on from a different runtime system, called “Substrate VM”. 
Substrate VM is the name for the runtime components (like the deoptimizer, garbage collector, thread scheduling etc.).
The resulting program has faster startup time and lower runtime memory overhead compared to a JVM.

SkyWalking currently offers support for native-image. However, please note that the OAP started in this manner does not have the same functionality as the regular OAP, and some features are not yet supported.

## Compile Guide
Notice: If you are not familiar with the compilation process, please read [How-to-build](https://skywalking.apache.org/docs/main/next/en/guides/how-to-build/) first.

The native-image compilation is not enabled by default. To enable it, we need to specify `-p native` during compilation, such as:

```shell
./mvnw -P native clean package -Dmaven.test.skip
```

Then, All packages are in `distribution/graal/dist` (.tar.gz for Linux and .zip for Windows).

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

## Problems
With native-image, some features are not yet supported.

1. [LAL](https://skywalking.apache.org/docs/main/next/en/concepts-and-designs/lal/), [MAL](https://skywalking.apache.org/docs/main/next/en/concepts-and-designs/mal/), and some other features related to them are not supported at the moment.
2. The configuration file for [OAL](https://skywalking.apache.org/docs/main/next/en/concepts-and-designs/oal/) can only be provided during the compilation period, meaning that it cannot be added or modified during runtime.
