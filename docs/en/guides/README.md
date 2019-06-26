# Guides
There are many ways that you can help the SkyWalking community.

- Go through our documents, point out or fixed unclear things. Translate the documents to other languages.
- Download our [releases](http://skywalking.apache.org/downloads/), try to monitor your applications, and feedback to us about 
what you think.
- Read our source codes, Ask questions for details.
- Find some bugs, [submit issue](https://github.com/apache/skywalking/issues), and try to fix it.
- Find [help wanted issues](https://github.com/apache/skywalking/issues?q=is%3Aopen+is%3Aissue+label%3A%22help+wanted%22),
which are good for you to start.
- Submit issue or start discussion through [GitHub issue](https://github.com/apache/skywalking/issues/new).
- See all mail list discussion through [website list review](https://lists.apache.org/list.html?dev@skywalking.apache.org).
If you are a SkyWalking committer, could login and use the mail list in browser mode. Otherwise, 
follow the next step to subscribe. 
- Issue report and discussion also could take place in `dev@skywalking.apache.org`. 
Mail to `dev-subscribe@skywalking.apache.org`, follow the reply to subscribe the mail list. 


## Contact Us
All the following channels are open to the community, you could choose the way you like.
* Submit an [issue](https://github.com/apache/skywalking/issues)
* Mail list: **dev@skywalking.apache.org**. Mail to `dev-subscribe@skywalking.apache.org`, follow the reply to subscribe the mail list.
* [Gitter](https://gitter.im/openskywalking/Lobby)
* QQ Group: 392443393

## For code developer
For developers, first step, read [Compiling Guide](How-to-build.md). It teaches developer how to build the project in local and set up the environment.

After setting up the environment and writing your codes, in order to make it more easily accepted by SkyWalking project, you'll
need to run the tests locally to verify that your codes don't break any existed features,
and write some unit test (UT) codes to verify that the new codes work well, preventing them being broke by future contributors.
If the new codes involve other components or libraries, you're also supposed to write integration tests (IT).

SkyWalking leverages plugin `maven-surefire-plugin` to run the UTs while using `maven-failsafe-plugin`
to run the ITs, `maven-surefire-plugin` will exclude ITs (whose class name starts with `IT`)
and leave them for `maven-failsafe-plugin` to run, which is bound to the `verify` goal, `CI-with-IT` profile.
Therefore, to run the UTs, try `./mvnw clean test`, this will only run the UTs, not including ITs.

If you want to run the ITs please activate the `CI-with-IT` profile
as well as the the profiles of the modules whose ITs you want to run.
e.g. if you want to run the ITs in `oap-server`, try `./mvnw -Pbackend,CI-with-IT clean verify`,
and if you'd like to run all the ITs, simple run `./mvnw -Pall,CI-with-IT clean verify`.

Please be advised that if you're writing integration tests, name it with the pattern `IT*` to make them only run in `CI-with-IT` profile.

### Project Extensions
SkyWalking project supports many ways to extend existing features. If you are interesting in these ways,
read the following guides.

- [Java agent plugin development guide](Java-Plugin-Development-Guide.md).
This guide helps you to develop SkyWalking agent plugin to support more frameworks. Both open source plugin
and private plugin developer should read this. 
- If you want to build a new probe or plugin in any language, please read [Component library definition and extension](Component-library-settings.md) document.
- [Storage extension development guide](storage-extention.md). Help potential contributors to build a new 
storage implementor besides the official.
- [Customize analysis by oal script](write-oal.md). Guide you to use oal script to make your own metrics available.
- [Source and scope extension for new metrics](source-extension.md). If you want to analysis a new metrics, which SkyWalking
haven't provide. You need to 
add a new receiver rather than choosing [existed receiver](../setup/backend/backend-receivers.md).
At that moment, 
you most likely need to add a new source and scope. This document will teach you how to do.
- [Backend Inventory entity extension](inventory-extension.md). If you want to extend SkyWalking inventory entities, and
want to push upstream back to our Apache OSS repo, please read these principles.

### UI developer
Our UI is constituted by static pages and web container.

- [RocketBot UI](https://github.com/apache/skywalking-rocketbot-ui) is SkyWalking primary UI since 6.1 release.
It is built with vue + typescript. You could know more at the rocketbot repository.
- **Web container** source codes are in `apm-webapp` module. This is a just an easy zuul proxy to host
static resources and send GraphQL query requests to backend.
- [Legacy UI repository](https://github.com/apache/skywalking-ui) is still there, but not included
in SkyWalking release, after 6.0.0-GA.

## For release
[Apache Release Guide](How-to-release.md) introduces to the committer team about doing official Apache version release, to avoid 
breaking any Apache rule. Apache license allows everyone to redistribute if you keep our licenses and NOTICE
in your redistribution. 
