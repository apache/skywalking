# Backend, UI, and CLI setup


SkyWalking backend distribution package includes the following parts:

1. **bin/cmd scripts**, in `/bin` folder. Includes startup linux shell and Windows cmd scripts for Backend
server and UI startup.

2. **Backend config**, in `/config` folder. Includes settings files of the backend, which are:
   * `application.yml`
   * `log4j.xml`
   * `alarm-settings.yml`

3. **Libraries of backend**, in `/oap-libs` folder. All the dependencies of the backend are in it.

4. **Webapp env**, in `webapp` folder. UI frontend jar file is here, with its `webapp.yml` setting file. 

## Quick start

### Requirements and default settings

Requirement: **JDK8 to JDK12 are tested**, other versions are not tested and may or may not work.

Before you start, you should know that the quickstart aims to get you a basic configuration mostly for previews/demo, performance and long-term running are not our goals. 

For production/QA/tests environments, you should head to [Backend and UI deployment documents](#deploy-backend-and-ui).

You can use `bin/startup.sh` (or cmd) to startup the backend and UI with their default settings, which are:

- Backend storage uses **H2 by default** (for an easier start)
- Backend listens `0.0.0.0/11800` for gRPC APIs and `0.0.0.0/12800` for http rest APIs.

In Java, .NetCore, Node.js, Istio agents/probe, you should set the gRPC service address to `ip/host:11800`, with ip/host where your backend is.
- UI listens on `8080` port and request `127.0.0.1/12800` to do GraphQL query.  

## Deploy Backend and UI

Before deploying Skywalking in your distributed environment, you should know how agents/probes, backend, UI communicates with each other:

<img src="https://skywalking.apache.org/doc-graph/communication-net.png"/>

- All native agents and probes, either language based or mesh probe, are using gRPC service (`core/default/gRPC*` in `application.yml`) to report data to the backend. Also, jetty service supported in JSON format. 
- UI uses GraphQL (HTTP) query to access the backend also in Jetty service (`core/default/rest*` in `application.yml`).

Now, let's continue with the backend, UI and CLI setting documents.
### [Backend setup document](backend-setup.md)
### [UI setup document](ui-setup.md)
### [CLI set up document](https://github.com/apache/skywalking-cli)
