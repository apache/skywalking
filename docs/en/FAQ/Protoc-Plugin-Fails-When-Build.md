**Problem**：
- When maven build the protoc-plugins to produce the error :
[ERROR] Failed to execute goal org.xolstice.maven.plugins:protobuf-maven-plugin:0.5.0:compile-custom (default) on project apm-network: Unable to copy the file to C:\workspace\incub
ator-skywalking\apm-network\target\protoc-plugins: C:\workspace\incubator-skywalking\apm-network\target\protoc-plugins\protoc-3.3.0-windows-x86_64.exe (另一个程序正在使用此文件，进
程无法访问。) -> [Help 1]

**Reason**：
- Protobuf compiler is dependent on the glibc, and some Linux operating systems do not install or upgrade the function library.

**Resolve**：
- check and upgrade the latest version of the glibc library.The container recommends using the latest glibc version of the alpine system.
Please refer to http://www.gnu.org/software/libc/documentation.html
