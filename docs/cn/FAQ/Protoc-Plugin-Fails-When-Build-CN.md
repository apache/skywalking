现象：
- Collector启动时maven编译加载protoc-plugins插件产生“另一个程序正在使用此文件，进程无法访问”的错误。

原因：
- Protobuf编译器依赖于glibc环境，部分linux操作系统未安装或未升级该函数库会产生该问题。

解决方法：
- 检查并升级最新版本glibc库，若使用容器镜像环境推荐含有最新版本glibc的alpine系统。请参考官方手册：http://www.gnu.org/software/libc/documentation.html
