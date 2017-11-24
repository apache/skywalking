# Skywalking 5 Dashboard Service
* In discussion only

## Background
Hontao Gao proposed to provide an independency skywalking dashboard in 5.x, by using a new NodeJS/Reactor technical stack.
For that, the services between Dashboard and Collector must be more clear than now. And both considering both side requirements.

## Goals
This document defines services, provided by collector, for query monitoring data and config monitoring parameters.
This service define works in the skywalking 5.x.

## Participators
Major PMC and Committer Team members: Sheng Wu, Hongtao Gao, Yongsheng Peng. And welcome any one to join us if you
are interested in this too.

## Service
### Protocol
All services provided to dashboard is in HTTP RESTful format. And the http body should stay in JSON format.