# Server Agents

Server agents in various languages provide auto-instrumentation or/and manual-instrumentation(APIs-based) mechanisms to
integrate with target services. They support collecting traces, logs, metrics, and events using SkyWalking's native
format and maximize the analysis capabilities of the SkyWalking OAP server.

## Installing language agents in services

- [Java agent](https://skywalking.apache.org/docs/skywalking-java/next/en/setup/service-agent/java-agent/readme/). Learn how to install the Java agent in your service without affecting your code.

- [LUA agent](https://github.com/apache/skywalking-nginx-lua). Learn how to install the Lua agent in Nginx + LUA module or OpenResty.

- [Kong agent](https://github.com/apache/skywalking-kong). Learn how to install the Lua agent in Kong.

- [Python Agent](https://skywalking.apache.org/docs/skywalking-python/next/en/setup/cli/). Learn how to install the Python Agent in a Python service without affecting your code.

- [Node.js agent](https://github.com/apache/skywalking-nodejs). Learn how to install the NodeJS Agent in a NodeJS service.

- [Rust agent](https://github.com/apache/skywalking-rust). Learn how to integrate the Rust agent with a rust service.

- [PHP agent](https://skywalking.apache.org/docs/skywalking-php/next/readme/). Learn how to install the PHP agent in your service without affecting your code.

- [Go agent](https://skywalking.apache.org/docs/skywalking-go/next/readme/). Learn how to integrate the Go agent with a golang service.

- [Ruby agent](https://skywalking.apache.org/docs/skywalking-ruby/next/readme/). Learn how to integrate the Ruby agent with a ruby service.

The following agents and SDKs are compatible with SkyWalking's data formats and network protocols but are maintained by
third parties. See their project repositories for guides and releases.

- [SkyAPM .NET Core agent](https://github.com/SkyAPM/SkyAPM-dotnet). See .NET Core agent project documentation for more
  details.

- [SkyAPM C++ SDK](https://github.com/SkyAPM/cpp2sky). See cpp2sky project documentation for more details.
