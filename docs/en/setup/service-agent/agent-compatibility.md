# Compatibility

SkyWalking 8.0+ uses v3 protocols. Agents don't have to keep the same versions as the OAP backend.

## SkyWalking Native Agents

|OAP Server Version|Java|Python|NodeJS|LUA|Kong|Browser Agent|
----------- | ---------- | --------- | --------- |--------- |--------- |--------- |
8.0.1 - 8.1.0 | 8.0.0 - 8.3.0 | < = 0.6.0| < = 0.3.0 | All | All | No |
8.2.0 - 8.3.0 | 8.0.0 - 8.3.0 | < = 0.6.0| < = 0.3.0 | All | All | All |
8.4.0+ | \> = 8.0.0 | All | All | All | All | All |

## Ecosystem Agents

All following agent implementations are a part of SkyWalking ecosystem. All the source codes and their distributions
don't belong to the Apache Software Foundation.

|OAP Server Version|DotNet|Go2sky|cpp2sky|PHP agent|
----------- | ---------- | --------- | --------- |--------- |
8.0.1 - 8.3.0 | 1.0.0 - 1.3.0 | 0.4.0 - 0.6.0 | < = 0.2.0 | \> = 3.0.0|
8.4.0+ | \> = 1.0.0 | \> = 0.4.0  | All | \> = 3.0.0|

All these projects are maintained by their own communities, please reach them if you face any compatibility issue.

___
All above compatibility are only references, if you face `unimplemented` error, it means you need to upgrade OAP backend
to support newer features in the agents.