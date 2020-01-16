# ParentSegmentNotFoundException

## Format
ParentSegmentNotFoundException<br/>
expected: PARENT_SEGMENT_EXPRESS<br/>
actual: NOT FOUND

## Cause 
The `ParentSegmentNotFoundException` caused by the parent segment express cannot found in the actual data file.<br/>
e.g.,
expected:
```
"refs": [
        {
          "parentTraceSegmentId": "${other_application[10]}",
          ...
        }
]
```
actual:
The segment size of `other_application` less than 10

## Check Points
1. Check if the parent segment express is correct