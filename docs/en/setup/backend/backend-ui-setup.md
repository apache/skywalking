# Backend and UI

SkyWalking backend distribution package includes following parts
1. **bin/cmd scripts**, in `/bin` folder. Include startup linux shell and Windows cmd scripts for Backend
server and UI startup.
1. **Backend config**, in `/config` folder. Include setting files of backend, which are `application.yml`,
`log4j.xml` and `alarm-settings.yml`. Most open settings are in these files.
1. **Libraries of backend**, in `/oap-libs` folder. All jar files of backend are in it.
1. **Webapp env**, in `webapp` folder. UI frontend jar file is in here and its `webapp.yml` setting file. 

## Quick start
Requirement: **JDK8**

Before you do quick start, you should know, quick start is to run skywalking backend and UI for preview
or demonstration. In here, performance and long-term running are not our goals. 

Want to deploy to product/test env? Go to [Backend and UI deployment documents](#deploy-backend-and-ui)

You can use `bin/startup.sh`(or cmd) to startup backend and UI in default settings, which include the following
things you need to know.
- Storage, use H2 by default, in order to make sure, don't need further deployment.
- Backend listens `0.0.0.0/11800` for gRPC APIs and `0.0.0.0/12800` for http rest APIs.
In Java, .NetCore, Node.js, Istio agents/probe, set the gRPC service address to `ip/host:11800`. 
(ip/host is where the backend at)
- UI listens `8080` port and request `127.0.0.1/12800` to do GraphQL query.  

## Deploy Backend and UI
After the quick start, you should want to deploy the backend and UI in the distributed env.
Before that, you should know how agent/probe, backend, UI communicate with each other.

<img src="https://skywalkingtest.github.io/page-resources/6.0.0/communication-net.png"/>

- All native agents and probes, either language based or mesh probe, are using gRPC service(`core/default/gRPC*` in `application.yml`) to report
data to backend. Also, jetty service supported in JSON format. 
- UI uses GraphQL(HTTP) query to access backend also in Jetty service(`core/default/rest*` in `application.yml`).

Now, let's continue with the backend and UI setting documents.
- [Backend setup document](backend-setup.md)
- [UI setup document](ui-setup.md)

 