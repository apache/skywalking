# SegmentRefSizeNotEqualsException
## Format
SegmentRefSizeNotEqualsException<br/>
expected: EXPECTED_SIZE<br/>
actual:  ACUTAL_SIZE

## Cause 
The `SegmentRefSizeNotEqualsException` caused by the size of `refs` is different between you expect and actual.

e.g.,
expected:
```
"refs": [
    {
        "parentTraceSegmentId": "${other_application[1]}",
         ...
    }
]
```
actual:
```
"refs": [
    {
        "parentTraceSegmentId": "${other_application[1]}",
         ...
    },
    {
        "parentTraceSegmentId": "${other_application[2]}",
         ...
    }
]
```
## Check Points
1. Check if the size of segment ref is correct