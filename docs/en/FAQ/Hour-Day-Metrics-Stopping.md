# Why do metrics indexes with Hour and Day precisions stop updating after upgrade to 7.x?

This issue is to be expected with an upgrade from 6.x to 7.x. 
See the [Downsampling Data Packing feature](../setup/backend/backend-storage.md#downsampling-data-packing)
of the ElasticSearch storage.

You may simply delete all expired `*-day_xxxxx` and `*-hour_xxxxx`(`xxxxx` is a timestamp) indexes. 
Currently, SkyWalking uses the `metrics name-xxxxx` and `metrics name-month_xxxxx` indexes only.
