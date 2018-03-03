# 部署步骤
1. 在Maven Central中下载 `org.apache.skywalking.apm-dist`. 或者本地编译程序，在`dist`目录中找到`skywalking-dist.tar.gz/.zip`.
1. 部署 Backend
   1. [单机模式](Deploy-backend-in-standalone-mode-CN.md)
   1. [集群模式](Deploy-backend-in-cluster-mode-CN.md)
1. 部署 Java Agent，[doc](Deploy-skywalking-agent-CN.md)
1. 重启并访问系统功能，查看UI即可。