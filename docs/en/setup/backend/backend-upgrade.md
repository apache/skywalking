# Backend upgrade
First and most important thing is, SkyWalking agent caching the key-value of the metadata, i.e., the service id and service name, the serice instance id and service instance name, the endpoint id and endpoint name. The backend will not working after you clear the metadata from elasticsearch database but not restart the agent.

**Remember to convert metadata from the old models to the new models.** 

There are three ways for upgrade SkyWalking.
1. Upgrade without transform metadata
1. Upgrade by transform metadata

## Upgrade without transform metadata
1. Shutdown the backend and clean the buffer data
1. Shutdown the database and clean the models
1. Download the release package and unpack it
1. Setting the `application.yml`
1. Setting the `gRPCPort` to be different from the old edition **It's very important**
1. Startup the database
1. Startup the backend 
1. Startup the agent one by one

## Upgrade by migration metadata
1. To view the model changes from the release note.
1. Write data migration scripts
1. Backup databse data
1. Migrate data to a new database
1. Startup the database
1. Startup the backend 
