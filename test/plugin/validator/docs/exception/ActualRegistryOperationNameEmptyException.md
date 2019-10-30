# ActualRegistryOperationNameEmptyException

## Format
ActualRegistryOperationNameEmptyException APPLICATION<br/>
expected: [ OPERATION_NAME_A, OPERATION_NAME_B, ... ]<br/>
actual:  Empty

## Cause
The `ActualRegistryOperationNameEmptyException` caused by there is any registry operation name in the actual data file

## Check Points
1. Check if the execute time of entry service of test case more than 40 seconds, Please make it less than 40 seconds.