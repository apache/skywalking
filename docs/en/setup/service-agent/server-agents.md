# Server Agents

Server agents in various languages provide auto-instrumentation or/and manual-instrumentation(APIs-based) mechanism to
integrate with target services. They support collecting traces, logs, metrics and events by using SkyWalking's native
format, and maximum the analysis capabilities of SkyWalking OAP server.

## Installing language agents in services

- [Java agent](http://github.com/apache/skywalking-java). Learn how to install the Java agent in your service without
  affecting your code.

- [LUA agent](https://github.com/apache/skywalking-nginx-lua). Learn how to install the Lua agent in Nginx + LUA module
  or OpenResty.

- [Kong agent](https://github.com/apache/skywalking-kong). Learn how to install the Lua agent in Kong.

- [Python Agent](https://github.com/apache/skywalking-python). Learn how to install the Python Agent in a Python
  service.

- [Node.js agent](https://github.com/apache/skywalking-nodejs). Learn how to install the NodeJS Agent in a NodeJS
  service.

- [Rust agent](https://github.com/apache/skywalking-rust). Learn how to integrate the rust agent in a rust service.

The following agents and SDKs are compatible with SkyWalking's data formats and network protocols, but are maintained by
third parties. See their project repositories for guides and releases.

- [SkyAPM .NET Core agent](https://github.com/SkyAPM/SkyAPM-dotnet). See .NET Core agent project document for more
  details.

- [SkyAPM PHP agent](https://github.com/SkyAPM/SkyAPM-php-sdk). See PHP agent project document for more details.

- [SkyAPM Go SDK](https://github.com/SkyAPM/go2sky). See go2sky project document for more details.

- [SkyAPM C++ SDK](https://github.com/SkyAPM/cpp2sky). See cpp2sky project document for more details.
