**Problem**：
- In manve build, the protoc-plugin occurs error:
```
[ERROR] Failed to execute goal org.xolstice.maven.plugins:protobuf-maven-plugin:0.5.0:compile-custom (default) on project apm-network: Unable to copy the file to \incubator-skywalking\apm-network\target\protoc-plugins: \incubator-skywalking\apm-network\target\protoc-plugins\protoc-3.3.0-linux-x86_64.exe (另一个程序正在使用此文件，进程无法访问。) -> [Help 1]
```

**Reason**：
- Protobuf compiler is dependent on the glibc, but it is not-installed or installed old version in the system.

**Resolve**：
- Install or upgrade to the latest version of the glibc library. In container env, recommend using the latest glibc version of the alpine system.
Please refer to http://www.gnu.org/software/libc/documentation.html
