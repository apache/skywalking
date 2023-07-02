# End to End Tests (E2E)
SkyWalking heavily rely more automatic tests to perform software quality assurance. E2E is an integral part of it.

> End-to-end testing is a methodology used to test whether the flow of an application is performing as designed from start to finish.
The purpose of carrying out end-to-end tests is to identify system dependencies and to ensure that the right information is passed between various system components and systems.

E2E in SkyWalking is always setting the OAP, monitored services and relative remote server dependencies in a real environment,
and verify the dataflow and ultimate query results.

The E2E test involves some/all of the OAP server, storage, coordinator, webapp, and the instrumented services, all of which are orchestrated by `docker-compose` or `KinD`.
Since version 8.9.0, we immigrate to e2e-v2 which leverage [skywalking-infra-e2e](https://github.com/apache/skywalking-infra-e2e) and [skywalking-cli](https://github.com/apache/skywalking-cli) to do the whole e2e process.
`skywalking-infra-e2e` is used to control the e2e process and `skywalking-cli` is used to interact with the OAP such as request and get response metrics from OAP.

## Writing E2E Cases

- Set up the environment
1. Set up `skywalking-infra-e2e`
1. Set up `skywalking-cli`, `yq` (generally these 2 are enough) and others tools if your cases need. Can reference the script under `skywalking/test/e2e-v2/script/prepare/setup-e2e-shell`.

- Orchestrate the components

The goal of the E2E tests is to test the SkyWalking project as a whole, including the OAP server, storage, coordinator, webapp, and even the frontend UI (not for now), on the single node mode as well as the cluster mode. Therefore, the first step is to determine what case we are going to verify, and orchestrate the
components.

To make the orchestration process easier, we're using a [docker-compose](https://docs.docker.com/compose/) that provides a simple file format (`docker-compose.yml`) for orchestrating the required containers, and offers an opportunity to define the dependencies of the components.

Follow these steps:
1. Decide what (and how many) containers will be needed. For example, for cluster testing, you'll need > 2 OAP nodes, coordinators (e.g. zookeeper), storage (e.g. ElasticSearch), and instrumented services;
1. Define the containers in `docker-compose.yml`, and carefully specify the dependencies, starting orders, and most importantly, link them together, e.g. set the correct OAP address on the agent end, and set the correct coordinator address in OAP, etc.
1. Define the e2e case [config](https://skywalking.apache.org/docs/skywalking-infra-e2e/next/en/setup/configuration-file/) in `e2e.yaml`.
1. Write the expected data(yml) for verify.

- [Run e2e test](https://skywalking.apache.org/docs/skywalking-infra-e2e/next/en/setup/run-e2e-tests/)

All e2e cases should under `skywalking/test/e2e-v2/cases`. You could execute e2e run command in `skywalking/` e.g.
```
e2e run -c test/e2e-v2/cases/alarm/h2/e2e.yaml
```

- Troubleshooting

We expose all logs from all containers to the stdout in the non-CI (local) mode, but save and upload them to the GitHub server. You can download them (only when the tests have failed) at "Artifacts/Download artifacts/logs" (see top right) for debugging.

**NOTE:** Please verify the newly-added E2E test case locally first. However, if you find that it has passed locally but failed in the PR check status, make sure that all the updated/newly-added files (especially those in the submodules)
are committed and included in the PR, or reset the git HEAD to the remote and verify locally again.
