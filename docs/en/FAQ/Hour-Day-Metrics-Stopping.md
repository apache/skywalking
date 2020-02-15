# Why metrics indexes in Hour and Day precisions stop update after upgrade to 7.x?

This is an expected case when 6.x->7.x upgrade. 
Read [Downsampling Data Packing feature](../setup/backend/backend-storage.md#downsampling-data-packing)
of the ElasticSearch storage.

The users could simply delete all expired `*-day_xxxxx` and `*-hour_xxxxx`(`xxxxx` is a timestamp) indexes. 
SkyWalking is using `metrics name-xxxxx` and `metrics name-month_xxxxx` indexes only.