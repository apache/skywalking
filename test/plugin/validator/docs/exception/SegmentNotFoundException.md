# SegmentNotFoundException

## Format
SegmentNotFoundException<br/>
expected<br/>
Segment:<br/>
  - span[PARENT_SPAN_ID, SPAN_ID] OPERATION_NAME_A<br/>
  - span[PARENT_SPAN_ID, SPAN_ID] OPERATION_NAME_B<br/>
  - ......<br/>

actual:<br/>
Segment[SEGMENT_ID_A] validate failed:<br/>
expected: span[PARENT_SPAN_ID, SPAN_ID] OPERATION_NAME<br/>
actual: span[PARENT_SPAN_ID, SPAN_ID] OPERATION_NAME<br/>
reason:  [SPAN_FIELD] expected=>{EXPECTED_VALUE}, actual=>{ACTUAL_VALUE}<br/>

Segment[SEGMENT_ID_B] validate failed:<br/>
expected: span[PARENT_SPAN_ID, SPAN_ID] OPERATION_NAME<br/>
actual: span[PARENT_SPAN_ID, SPAN_ID] OPERATION_NAME<br/>
reason:  [SPAN_FIELD] expected=>{EXPECTED_VALUE}, actual=>{ACTUAL_VALUE}<br/>

......<br/>

**NOTE**:  The validate tool validates all similar segment similar to what you expect in the actual data file.  The rule that the validate tool to check if the segment similar to what you expect is the span size equals.

## Cause 
The `SegmentNotFoundException` caused by the segment what you expect cannot found in the actual data file.

## Check Points 
1. Check if the span sequence of a segment is right
2. Check if  the field value of the span is right