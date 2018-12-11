# Write OAL script
Read [OAL overview](../concepts-and-designs/oal.md) to understand the oal script.

## Find oal script in source codes
The official official_analysis.oal is [here](../../../oap-server/generated-analysis/src/main/resources/official_analysis.oal).

## Generate tool
The `oap-server/generate-tool` module includes the source codes of compiling tool. This tool is already integrated
maven compile phase. So, unless you want to change the tool source codes, you don't need to set anything.

Run `./mvnw compile` or `./mvnw package`, the generated codes of the oal script are in `oap-server/generate-tool/target/generated-sources/oal/*`.

## Write and recompile
You could change the `official_analysis.oal` script, then recompile codes.
The generated codes are in **oap-server/generated-analysis/target/generated-sources/oal**.