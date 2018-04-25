**Problem**：
- When the Collector starts, it loads the protoc-plugins to produce the error “No such file or directory”.

**Reason**：
- Protobuf compiler is dependent on the glibc, and some Linux operating systems do not install or upgrade the function library.

**Resolve**：
- check and upgrade the latest version of the glibc library.The container recommends using the latest glibc version of the alpine system.
