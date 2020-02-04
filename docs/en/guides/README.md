# Guides
There are many ways that you can help the SkyWalking community.

- Go through our documents, point out or fix unclear things. Translate the documents to other languages.
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

## Become official Apache SkyWalking Committer
The PMC will assess the contributions of every contributor, including, but not limited to, 
code contributions, and follow the Apache guides to promote, vote and invite new committer and PMC member.
Read [Become official Apache SkyWalking Committer](asf/committer.md) to get details.

## For code developer
For developers, first step, read [Compiling Guide](How-to-build.md). It teaches developer how to build the project in local and set up the environment.

### Integration Tests
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
and if you'd like to run all the ITs, simply run `./mvnw -Pall,CI-with-IT clean verify`.

Please be advised that if you're writing integration tests, name it with the pattern `IT*` to make them only run in `CI-with-IT` profile.

### End to End Tests (E2E for short)
Since version 6.3.0, we have introduced more automatic tests to perform software quality assurance, E2E is one of the most important parts.

> End-to-end testing is a methodology used to test whether the flow of an application is performing as designed from start to finish.
 The purpose of carrying out end-to-end tests is to identify system dependencies and to ensure that the right information is passed between various system components and systems.

The e2e tests involves the OAP server, Web App, and the instrumented services, all of them run in a designed Docker container, connecting to each other; 
in addition, there is a test controller running outside of the container that sends requests to the instrumented service,
and then verifies the corresponding results after those requests, by GraphQL API of the SkyWalking Web App.

Run the command `./mvnw -f test/e2e/pom.xml clean verify` under the root directory to get an intuition on how they work together(note that it may take a long time).

#### Writing E2E Cases

- Set up environment in Intellij IDEA

The e2e test is an individual project under the SkyWalking root directory and the IDEA cannot recognize it by default, right click
on the file `test/e2e/pom.xml` and click `Add as Maven Project`, things should be ready now.

- Orchestrate the components

Our goal of E2E tests is to test the SkyWalking project in a whole, including the OAP server, Web App, and even the frontend UI(not now),
in single node mode as well as cluster mode, therefore the first step is to determine what case we are going to verify and orchestrate the 
components.
 
In order to make it more easily to orchestrate, we're using a [Docker e2e-container](https://github.com/SkyAPMTest/e2e-container) that can run
OAP server in both standalone and cluster mode, it can also run the given instrumented services. Refer to the repository for more details;

Basically you will need:
1. (If in cluster mode) start up the third-party service containers that SkyWalking depends on, such as ZooKeeper as coordinator, ElasticSearch as storage, etc. in the docker-maven-plugin;
1. (If in cluster mode) carefully map the addresses/ports into the e2e-container that can be used by the OAP server to connect to;
1. Start the OAP server, Web App, and instrumented services in the e2e-container, mount your customized startup script to `/rc.d/` in e2e-container and it gets run when the container starts up;
1. Use the utilities provided in e2e-container to check the healthiness of the components;
1. Design the test controller and verify expected result;

We've given a simple example that verifies SkyWalking should work as expected in cluster mode, refer to [the codes](../../../test/e2e/e2e-cluster) for detail.

- Write test controller

To put it simple, test controllers are basically tests that can be bound to the Maven `integration-test/verify` phase.
They send **designed** requests to the instrumented service, and expect to get corresponding traces/metrics/metadata from the SkyWalking webapp GraphQL API.

- Troubleshooting

**NOTE:** Please verify the newly-added E2E test case locally first, however, if you find it passed locally but failed in the PR check status, make sure all the updated/newly-added files (especially those in submodules)
are committed and included in that PR, or reset the git HEAD to the remote and verify locally again.

There should be related logs when tests are failed, but if the OAP backend failed to start at all, there may be just logs saying "timeout", please verify locally again, and recheck the NOTE above.

### Project Extensions
SkyWalking project supports many ways to extend existing features. If you are interesting in these ways,
read the following guides.

- [Java agent plugin development guide](Java-Plugin-Development-Guide.md).
This guide helps you to develop SkyWalking agent plugin to support more frameworks. Both open source plugin
and private plugin developer should read this. 
- If you want to build a new probe or plugin in any language, please read [Component library definition and extension](Component-library-settings.md) document.
- [Storage extension development guide](storage-extention.md). Help potential contributors to build a new 
storage implementor besides the official.
- Customize analysis by oal script. OAL scripts locate in `config/official_analysis.oal`. You could change it and reboot the OAP server. Read 
[Observability Analysis Language Introduction](../concepts-and-designs/oal.md) if you need to learn about OAL script.
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

### OAP backend dependency management
> This section is only applicable to the dependencies of the backend module

Being one of the Top Level Projects of The Apache Software Foundation (ASF),
SkyWalking is supposed to follow the [ASF 3RD PARTY LICENSE POLICY](https://apache.org/legal/resolved.html),
so if you're adding new dependencies to the project, you're responsible to check the newly-added dependencies
won't break the policy, and add their LICENSE's and NOTICES's to the project.

We have a [simple script](../../../tools/dependencies/check-LICENSE.sh) to help you make sure that you didn't
miss any newly-added dependency:
- Build a distribution package and unzip/untar it to folder `dist`.
- Run the script in the root directory, it will print out all newly-added dependencies.
- Check the LICENSE's and NOTICE's of those dependencies, if they can be included in an ASF project, add them in the `apm-dist/release-docs/{LICENSE,NOTICE}` file.
- Add those dependencies' names to the `tools/dependencies/known-oap-backend-dependencies.txt` file (**alphabetical order**), the next run of `check-LICENSE.sh` should pass. 

## Profile
The performance profile is an enhancement feature in the APM system. We are using the thread dump to estimate the method execution time, rather than adding many local spans. In this way, the resource cost would be much less than using distributed tracing to locate slow method. This feature is suitable in the production environment. The following documents are important for developers to understand the key parts of this feature
- [Profile data report procotol](https://github.com/apache/skywalking-data-collect-protocol/tree/master/profile) is provided like other trace, JVM data through gRPC.
- [Thread dump merging mechanism](backend-profile.md) introduces the merging mechanism, which helps the end users to understand the profile report.

## For release
[Apache Release Guide](How-to-release.md) introduces to the committer team about doing official Apache version release, to avoid 
breaking any Apache rule. Apache license allows everyone to redistribute if you keep our licenses and NOTICE
in your redistribution. 
