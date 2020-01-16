# SegmentRefNotFoundException

## Format
SegmentRefNotFoundException<br/>
expected:<br/>
SegmentRef:<br/>
  - entryServiceName: ENTRY_SERVICE_NAME<br/>
  - networkAddress: NETWORK_ADDRESS<br/>
  - parentServiceName: PARENT_SERVICE_NAME<br/>
  - parentSpanId:  PARENT_SPAN_ID<br/>
  - parentTraceSegmentId: PARENT_TRACE_SEGMENT_ID<br/>
  - refType: REF_TYPE<br/>

actual:<br/>
SegmentRef:  [SEGMENT_REF_FIELD] expected=>{EXPECTED_SIZE}, actual=>{ACTUAL_SIZE}<br/>
  - entryServiceName: ENTRY_SERVICE_NAME<br/>
  - networkAddress: NETWORK_ADDRESS<br/>
  - parentServiceName: PARENT_SERVICE_NAME<br/>
  - parentSpanId:  PARENT_SPAN_ID<br/>
  - parentTraceSegmentId: PARENT_TRACE_SEGMENT_ID<br/>
  - refType: REF_TYPE<br/>
......

**NOTE**: The validate tool check all the segment ref of the span.

## Cause 
The  `SegmentRefNotFoundException` caused by the segment ref cannot found in the actual data file

## Check Points
1. Check if the field value of segment ref  what you expect is correct