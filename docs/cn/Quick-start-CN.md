
>new version
# 快速开始
Quick start is for end users to start up the SkyWalking quickly in local.

1. Download releases from Apache official website. [Link](http://skywalking.apache.org/downloads/)
1. Deploy backend on local. See [collector in standalone mode doc](Deploy-backend-in-standalone-mode.md)
1. Install Java Agent. [Doc](Deploy-skywalking-agent.md)
1. Set your OS time(include time zone) correct.
1. Reboot your applications, and open UI.
1. Default username/password for the login page is `admin/admin`.

If you want to deploy SkyWalking in server or cloud as a cluster, please following these two documents:
1. [Install javaagent](Deploy-skywalking-agent.md)
1. [Deploy backend in cluster mode](Deploy-backend-in-cluster-mode.md)



>old version
# 部署步骤
1. 下载`apache-skywalking-apm-incubating-x.y.z.tar.gz` 或 `apache-skywalking-apm-incubating-x.y.z.zip`
1. 部署 Backend
   1. [单机模式](Deploy-backend-in-standalone-mode-CN.md)
   1. [集群模式](Deploy-backend-in-cluster-mode-CN.md)
1. 部署 Java Agent，[doc](Deploy-skywalking-agent-CN.md)
1. 重启并访问系统功能，查看UI即可。
1. 登录页面的默认用户名/密码是`admin/admin`。






