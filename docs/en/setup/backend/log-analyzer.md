# Log Analysis

Log analyzer of OAP server supports native log data. OAP could use Log Analysis Language to
structure log content through parsing, extracting and saving logs. 
The analyzer also uses Meter Analysis Language Engine for further metrics calculation.

```yaml
log-analyzer:
  selector: ${SW_LOG_ANALYZER:default}
  default:
    lalFiles: ${SW_LOG_LAL_FILES:default}
    malFiles: ${SW_LOG_MAL_FILES:""}
```

Read the doc on [Log Analysis Language(LAL)](../../concepts-and-designs/lal.md) for more on log structuring and metrics analysis.
The [LAL's `metrics` extracts](../../concepts-and-designs/lal.md#extractor) provide the capabilities to generate new metrics
from the raw log text for further calculation.
