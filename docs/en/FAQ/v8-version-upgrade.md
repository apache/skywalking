# V8 upgrade
SkyWalking v8 begins to use [v3 protocol](../protocols/README.md), so, it is incompatible with previous releases.
Users who intend to upgrade in v8 series releases could follow this guidance.


Register in v6 and v7 has been removed in v8 for better scaling out performance, please upgrade in the following ways.
1. Use a different storage or a new namespace. Also, could consider erasing the whole storage index/table(s) related to SkyWalking.
1. Deploy the whole SkyWalking cluster, and expose in a new network address.
1. If you are using the language agents, upgrade the new agents too, meanwhile, make sure the agent has supported the different language.
And set up the backend address to the new SkyWalking OAP cluster.