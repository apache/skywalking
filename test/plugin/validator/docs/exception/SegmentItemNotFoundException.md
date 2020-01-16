# SegmentItemNotFoundException

## Format
SegmentItemNotFoundException<br/>
expected:<br/>
Segment Item[APPLICATION]<br/>
 - segment size: EXPECTED_SIZE<br/>

actual: NOT FOUND

## Cause
The `SegmentItemNotFoundException` caused by the segment item of you expected cannot found in the actual data file

## Check Points
1. Check if the execute time of entry service of test case more than 40 seconds, Please make it less than 40 seconds.