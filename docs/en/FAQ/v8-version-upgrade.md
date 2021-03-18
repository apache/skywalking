# V8 upgrade
Starting from SkyWalking v8, the [v3 protocol](../protocols/README.md) has been used. This makes it incompatible with previous releases.
Users who intend to upgrade in v8 series releases could follow the steps below.


Registers in v6 and v7 have been removed in v8 for better scaling out performance. Please upgrade following the instructions below.
1. Use a different storage or a new namespace. You may also consider erasing the whole storage indexes or tables related to SkyWalking.
2. Deploy the whole SkyWalking cluster, and expose it in a new network address.
3. If you are using language agents, upgrade the new agents too; meanwhile, make sure the agents are supported in a different language.
Then, set up the backend address to the new SkyWalking OAP cluster.
