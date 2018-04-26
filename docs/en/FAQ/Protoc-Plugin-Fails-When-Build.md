**Problem**：
- When maven build the protoc-plugins to produce the error “(另一个程序正在使用此文件，进程无法访问)”.

**Reason**：
- Protobuf compiler is dependent on the glibc, and some Linux operating systems do not install or upgrade the function library.

**Resolve**：
- check and upgrade the latest version of the glibc library.The container recommends using the latest glibc version of the alpine system.
Please refer to http://www.gnu.org/software/libc/documentation.html
