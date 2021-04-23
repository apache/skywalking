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

The e2e test involves some/all of the OAP server, storage, coordinator, webapp, and the instrumented services, all of which are orchestrated by `docker-compose`,
besides, there is a test controller(JUnit test) running outside of the container that sends traffics to the instrumented service,
and then verifies the corresponding results after those requests, by GraphQL API of the SkyWalking Web App.

Before all following steps, please set the SkyWalking version `sw.version` in the [pom.xml](../../../test/e2e/pom.xml)
so that you can build it in your local IDE, but please make sure not to check this change into the codebase. However, if
you prefer to build it in command line interface with `./mvnw`, you can simply use property `-Dsw.version=x.y.z` without
modifying the pom.xml.

#### Writing E2E Cases

- Set up environment in IntelliJ IDEA

The e2e test is an separated project under the SkyWalking root directory and the IDEA cannot recognize it by default, right click
on the file `test/e2e/pom.xml` and click `Add as Maven Project`, things should be ready now. But we recommend to open the directory `skywalking/test/e2e`
in a separated IDE window for better experience because there may be shaded classes issues.

- Orchestrate the components

Our goal of E2E tests is to test the SkyWalking project in a whole, including the OAP server, storage, coordinator, webapp, and even the frontend UI(not now),
in single node mode as well as cluster mode, therefore the first step is to determine what case we are going to verify and orchestrate the 
components.
 
In order to make it more easily to orchestrate, we're using a [docker-compose](https://docs.docker.com/compose/) that provides a convenient file format (`docker-compose.yml`)
to orchestrate the needed containers, and gives us possibilities to define the dependencies of the components.

Basically you will need:
1. Decide what (and how many) containers will be needed, e.g. for cluster testing, you'll need > 2 OAP nodes, coordinators like zookeeper, storage like ElasticSearch, and instrumented services;
1. Define the containers in `docker-compose.yml`, and carefully specify the dependencies, starting orders, and most importantly, link them together, e.g. set correct OAP address in the agent side, set correct coordinator address in OAP, etc.
1. Write (or hopefully reuse) the test codes, to verify the results is correct.

As for the last step, we have a friendly framework to help you get started more quickly, which provides annotation `@DockerCompose("docker-compose.yml")` to load/parse and start up all the containers in a proper order,
`@ContainerHost`/`@ContainerPort` to get the real host/port of the container, `@ContainerHostAndPort` to get both, `@DockerContainer` to get the running container.

- Write test controller

To put it simple, test controllers are basically tests that can be bound to the Maven `integration-test/verify` phase.
They send **designed** requests to the instrumented service, and expect to get corresponding traces/metrics/metadata from the SkyWalking webapp GraphQL API.

In the test framework, we provide a `TrafficController` to periodically send traffic data to the instrumented services, you can simply enable it by giving a url and traffic data, refer to [this](../../../test/e2e/e2e-test/src/test/java/org/apache/skywalking/e2e/base/TrafficController.java).

- Troubleshooting

We expose all the logs from all containers to the stdout in non-CI (local) mode, but save/and upload them all to the GitHub server and you can download them (only when tests failed) in the right-up button "Artifacts/Download artifacts/logs" for debugging.

**NOTE:** Please verify the newly-added E2E test case locally first, however, if you find it passed locally but failed in the PR check status, make sure all the updated/newly-added files (especially those in submodules)
are committed and included in that PR, or reset the git HEAD to the remote and verify locally again.

#### E2E local remote debugging
When the E2E test is executed locally, if any test case fails, the [E2E local remote debugging function](E2E-local-remote-debug.md) can be used to quickly troubleshoot the bug.

### Project Extensions
SkyWalking project supports many ways to extend existing features. If you are interesting in these ways,
read the following guides.

- [Java agent plugin development guide](Java-Plugin-Development-Guide.md).
This guide helps you to develop SkyWalking agent plugin to support more frameworks. Both open source plugin
and private plugin developer should read this. 
- If you want to build a new probe or plugin in any language, please read [Component library definition and extension](Component-library-settings.md) document.
- [Storage extension development guide](storage-extention.md). Help potential contributors to build a new 
storage implementor besides the official.
- Customize analysis by oal script. OAL scripts locate in `config/oal/*.oal`. You could change it and reboot the OAP server. Read 
[Observability Analysis Language Introduction](../concepts-and-designs/oal.md) if you need to learn about OAL script.
- [Source and scope extension for new metrics](source-extension.md). If you want to analysis a new metrics, which SkyWalking
haven't provide. You need to 
add a new receiver rather than choosing [existed receiver](../setup/backend/backend-receivers.md).
At that moment, 
you most likely need to add a new source and scope. This document will teach you how to do.

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
- [Profile data report protocol](https://github.com/apache/skywalking-data-collect-protocol/tree/master/profile) is provided like other trace, JVM data through gRPC.
- [Thread dump merging mechanism](backend-profile.md) introduces the merging mechanism, which helps the end users to understand the profile report.
- [Exporter tool of profile raw data](backend-profile-export.md) introduces when the visualization doesn't work well through the official UI, how to package the original profile data, which helps the users report the issue.

## For release
[Apache Release Guide](How-to-release.md) introduces to the committer team about doing official Apache version release, to avoid 
breaking any Apache rule. Apache license allows everyone to redistribute if you keep our licenses and NOTICE
in your redistribution. 
