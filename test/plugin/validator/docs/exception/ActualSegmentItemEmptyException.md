# ActualSegmentItemEmptyException

## Format
ActualSegmentItemEmptyException<br/>
expected:<br/>
Segment Item[APPLICATION]<br/>
 - segment size: EXPECTED_SIZE<br/>

actual: Empty<br/>

## Cause
The `ActualSegmentItemEmptyException` caused by the segment item is empty in the actual data file

## Check Points
1. Check if the execute time of entry service of test case more than 40 seconds, Please make it less than 40 seconds.