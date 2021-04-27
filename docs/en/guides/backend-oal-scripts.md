# Official OAL script
First, read the [OAL introduction](../concepts-and-designs/oal.md).

From 8.0.0, you may find the OAL script at `/config/oal/*.oal` of the SkyWalking dist.
You could change it, such as by adding filter conditions or new metrics. Then, reboot the OAP server and it will come into effect.

All metrics named in this script may be used in alarm and UI query. 

Note: If you try to add or remove certain metrics, there is a possibility that the UI would break. You should only do this when you plan
to build your own UI based on the customization analysis core. 
