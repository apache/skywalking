# 支持传输层安全TLS(Transport Layer Security)
在通过Internet传输数据时，传输层安全（TLS）是一种非常常见的安全方式
用户可能会在一些场景下遇到这样的情形:

> 被监控(部署探针)的应用中部署在同一个私有云(VPC)区域当中,与此同时, SkyWalking 的服务端部署在另一个私有云(VPC)区域中
> 
> 在这种情况下,就非常有必要做一些传输安全认证.

## 配置要求
开启 **服务直连** 功能, 详情参考 [文档](Direct-uplink-CN.md).

由于通过公网直接上报数据，由于安全问题，名称(naming)服务机制并不适合这种情况.所以我们在HTTP服务的名称服务中不支持TLS。

## 版本支持
5.0.0-beta +

## 认证模式
仅仅支持 **非双向认证**.
- 如果你比较熟悉如何生成 key 文件,可以使用 [脚本](../../tools/TLS/tls_key_generate.sh) .
- 在客户端使用 `ca.crt`文件
- 在服务端使用 `server.crt` 和 `server.pem`. 

## 配置并开启 TLS

### 探针配置
- 将 `ca.crt` 放置在探针文件夹的 `/ca` 文件夹中. 需要注意的是,发行的版本中不包含`/ca`文件夹,需要自行创建.

如果探针检测到文件 `/ca/ca.crt` ,会自动开启 TLS.

### Collector 配置
 `agent_gRPC/gRPC` 模块支持 TLS. 并且现在只有这个模块支持.

- 将`application.yml`中的 `ssl_cert_chain_file` 和 `ssl_private_key_file`  配置打开.
- `ssl_cert_chain_file` 配置为 `server.crt`的绝对路径.
- `ssl_private_key_file` 配置为 `server.pem`的绝对路径.

## 避免端口共享
在大多数情况下，我们建议在`agent_gRPC / gRPC`和`remote / gRPC`模块中共享所有gRPC服务的端口。
但是，当你在`agent_gRPC / gRPC`模块中打开TLS时不要这样做，原因就是无论是否开始TLS,你都无法监听端口。
解决方案, 换一个端口 `remote/gRPC/port`.

## 其他端口监听如何操作?
请使用其他安全方式确保不能访问  VPC 区域外的其他端口，例如防火墙，代理等。