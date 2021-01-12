# Official OAL script
First, read [OAL introduction](../concepts-and-designs/oal.md).

Find OAL script at the `/config/oal/*.oal` of SkyWalking dist, since 8.0.0.
You could change it(such as adding filter condition, or add new metrics) and reboot the OAP server, then it will affect.

All metrics named in this script could be used in alarm and UI query. 

Notice,

If you try to add or remove some metrics, UI may break, we only recommend you to do this when you plan
to build your own UI based on the customization analysis core. 