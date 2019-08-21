# Start up mode
In different deployment tool, such as k8s, you may need different startup mode.
We provide another two optional startup modes.

## Default mode
Default mode. Do initialization works if necessary, start listen and provide service. 

Run `/bin/oapService.sh`(.bat) to start in this mode. Also when use `startup.sh`(.bat) to start.

## Init mode
In this mode, oap server starts up to do initialization works, then exit.
You could use this mode to init your storage, such as ElasticSearch indexes, MySQL and TiDB tables,
and init data.

Run `/bin/oapServiceInit.sh`(.bat) to start in this mode.

## No-init mode
In this mode, oap server starts up without initialization works,
but it waits for ElasticSearch indexes, MySQL and TiDB tables existed,
start listen and provide service. Meaning,
this oap server expect another oap server to do the initialization.

Run `/bin/oapServiceNoInit.sh`(.bat) to start in this mode.