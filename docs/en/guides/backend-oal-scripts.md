# Official OAL script
First, read [OAL introduction](../concepts-and-designs/oal.md).

Here is the official scrips is the `generated-analysis-x.y.z.jar/official_analysis.oal` file in distribution,
also the [official_analysis.oal](../../../oap-server/generated-analysis/src/main/resources/official_analysis.oal) in source code repository.

**Notice**, this file doesn't effect anything in runtime, although included in distribution.
You need to use OAL tool code generator to build the real analysis codes from it.
All generated codes are under `oal` folder in **oap-server/generated-analysis/target/generated-sources**.

All metrics named in this script could be used in alarm and UI query. Of course, you can change this 
scripts and re-generate the analysis process and metric, such as adding filter condition. 

If you try to add or remove some metric, UI may break, we only recommend you to do this when you plan
to build your own UI based on the customization analysis core. 