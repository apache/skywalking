# Guides
There are many ways that you can help the SkyWalking community.

- Go through our documents, point out or fixed unclear things. Translate the documents to other languages.
- Download our [releases](http://skywalking.apache.org/downloads/), try to monitor your applications, and feedback to us about 
what you think.
- Read our source codes, Ask questions for details.
- Find some bugs, [submit issue](https://github.com/apache/incubator-skywalking/issues), and try to fix it.
- Find [help wanted issues](https://github.com/apache/incubator-skywalking/issues?q=is%3Aopen+is%3Aissue+label%3A%22help+wanted%22),
which are good for you to start.
- Submit issue or start discussion through [GitHub issue](https://github.com/apache/incubator-skywalking/issues/new).
- See all mail list discussion through [website list review](https://lists.apache.org/list.html?dev@skywalking.apache.org).
If you are a SkyWalking committer, could login and use the mail list in browser mode. Otherwise, 
follow the next step to subscribe. 
- Issue report and discussion also could take place in `dev@skywalking.apache.org`. 
Mail to `dev-subscribe@skywalking.apache.org`, follow the reply to subscribe the mail list. 


## Contact Us
All the following channels are open to the community, you could choose the way you like.
* Submit an [issue](https://github.com/apache/incubator-skywalking/issues)
* Mail list: **dev@skywalking.apache.org**. Mail to `dev-subscribe@skywalking.apache.org`, follow the reply to subscribe the mail list.
* [Gitter](https://gitter.im/openskywalking/Lobby)
* QQ Group: 392443393

## For code developer
As a develop, first step, read [Compiling Guide](How-to-build.md). It teaches developer how to build the project in local.

### Project Extensions
SkyWalking project supports many ways to extends existing features. If you are interesting in these ways,
read the following guides.

- [Java agent plugin development guide](Java-Plugin-Development-Guide.md).
This guide helps you to develop SkyWalking agent plugin to support more frameworks. Both open source plugin
and private plugin developer should read this. 
- If you want to build a new probe or plugin in any language, please read [Component library definition and extension](Component-library-settings.md) document.
- [Storage extension development guide](storage-extention.md). Help potential contributors to build a new 
storage implementor besides the official.
- [Customize analysis by oal script](write-oal.md). Guide you to use oal script to make your own metric available.
- [Backend Inventory entity extension](inventory-extension.md). If you want to extend SkyWalking inventory entities, and
want to push upstream back to our Apache OSS repo, please read these principles.

### UI developer
Our UI is constituted by static pages and web container.

- **Static pages** is built based on [Ant Design Pro](https://pro.ant.design/), which source codes are 
hosted in our [UI repository](https://github.com/apache/incubator-skywalking-ui).
- **Web container** source codes are in `apm-webapp` module. This is a just an easy zuul proxy to host
static resources and send GraphQL query requests to backend.

## For release
[Apache Release Guide](How-to-release.md) introduces to the committer team about doing official Apache version release, to avoid 
breaking any Apache rule. Apache license allows everyone to redistribute if you keep our licenses and NOTICE
in your redistribution. 