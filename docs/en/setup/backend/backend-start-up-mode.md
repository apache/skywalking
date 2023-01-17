# Start up mode
You may need different startup modes in different deployment tools, such as k8s.
We provide two additional optional startup modes.

## Default mode
The default mode carries out tasks to initialize as necessary, starts to listen, and provides services. 

Run `/bin/oapService.sh`(.bat) to start in this mode. This is also applicable when you're using `startup.sh`(.bat) to start.

## Init mode
In this mode, the OAP server starts up to carry out initialization and then exits.
You could use this mode to initialize your storage (such as ElasticSearch indexes, MySQL, and TiDB tables) as well as your data.

Run `/bin/oapServiceInit.sh`(.bat) to start in this mode.

## No-init mode
In this mode, the OAP server starts up without carrying out initialization. Rather, it watches out for the ElasticSearch indexes, MySQL, TiDB and other storage tables, starts listening and provides services. In other words, the OAP server would anticipate having another OAP server carrying out the initialization.

Run `/bin/oapServiceNoInit.sh`(.bat) to start in this mode.
