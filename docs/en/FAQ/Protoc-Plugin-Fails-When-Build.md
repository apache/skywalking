### Problem
- In maven build, the following error may occur with the protoc-plugin:
```
[ERROR] Failed to execute goal org.xolstice.maven.plugins:protobuf-maven-plugin:0.5.0:compile-custom (default) on project apm-network: Unable to copy the file to \skywalking\apm-network\target\protoc-plugins: \skywalking\apm-network\target\protoc-plugins\protoc-3.3.0-linux-x86_64.exe (The process cannot access the file because it is being used by another process) -> [Help 1]
```

### Reason
- The Protobuf compiler is dependent on the glibc. However, glibc has not been installed, or there is an old version already installed in the system.

### Resolution
- Install or upgrade to the latest version of the glibc library. Under the container environment, the latest glibc version of the alpine system is recommended.
Please refer to http://www.gnu.org/software/libc/documentation.html.
