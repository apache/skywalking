# Guides
Guides help everyone developer, including PPMC member, committer and contributor, to understand the project structure. 
Also learn to build the project, even to release the official Apache version(If you have been accepted as the formal committer).

- [Compiling Guide](How-to-build.md). Teaches developer how to build the project in local.
- [Apache Release Guide](How-to-release.md). Apache license allows everyone to redistribute if you keep our licenses and NOTICE
in your redistribution. This document introduces to the committer team about doing official Apache version release, to avoid 
breaking any Apache rule.

## Project Extensions
SkyWalking project supports many ways to extends existing features. If you are interesting in these ways,
read the following guides.

- [Java agent plugin development guide](Java-Plugin-Development-Guide.md).
This guide helps you to develop SkyWalking agent plugin to support more frameworks. Both open source plugin
and private plugin developer should read this. 
- If you want to build a new probe or plugin in any language, please read [Component library definition and extension](Component-library-settings.md) document.
- [Storage extension development guide](storage-extention.md). Help potential contributors to build a new 
storage implementor besides the official.


## UI developer
Our UI is constituted by static pages and web container.

- **Static pages** is built based on [Ant Design Pro](https://pro.ant.design/), which source codes are 
hosted in our [UI repository](https://github.com/apache/incubator-skywalking-ui).
- **Web container** source codes are in `apm-webapp` module. This is a just an easy zuul proxy to host
static resources and send GraphQL query requests to backend.
