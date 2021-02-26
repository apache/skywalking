## Log Analyzer

Log analyzer supports native log data. OAP could use Log Analysis Language to
structurize log content through parse, extract, and save logs. 
Also the analyzer leverages Meter Analysis Language Engine for further metrics calculation.

```yaml
log-analyzer:
  selector: ${SW_LOG_ANALYZER:default}
  default:
    lalFiles: ${SW_LOG_LAL_FILES:default}
    malFiles: ${SW_LOG_MAL_FILES:""}
```

Read [Log Analysis Language](../../concepts-and-designs/lal.md) documentation to learn log structurize and metrics analysis.