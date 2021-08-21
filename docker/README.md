This folder contains Docker files for SkyWalking developers/committers to build images manually. If you want to start a SkyWalking backend server with docker-compose for your integration, please visit https://github.com/apache/skywalking-docker repository. 


## Quickstart
You can use `Makefile` located at the root folder to build a docker image with the current codebase.

```
make docker
```

It doesn't contain the process of building a docker image but also includes all the required steps, for instance, init workspace, build artifact from scratch. It builds two images, OAP, and UI.

```
docker image ls | grep skywalking
skywalking/ui                                   latest              a14db4e1d70d        6 minutes ago       800MB
skywalking/oap                                  latest              2a6084450b44        6 minutes ago       862MB
```


## Building variables

There're some environment variables to control image building.

### HUB

The hub of docker image. The default value is `skywalking`.

### TAG

The tag of docker image. The default value is `latest`.

## Running containers with docker-compose

We can start up backend cluster by docker-compose
```
cd docker
docker compose up
```
`docker/.env` file contains the default `TAG` and elasticsearch tag(`ES_TAG`).
