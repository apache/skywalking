# Integration Tests
IT(Integration Tests) represents the JUnit driven integration test to verify the features and compatibility between lib
and known server with various versions.

After setting up the environment and writing your codes, to facilitate integration with the SkyWalking project, you'll
need to run tests locally to verify that your codes would not break any existing features,
as well as write some unit test (UT) codes to verify that the new codes would work well. This will prevent them from being broken by future contributors.
If the new codes involve other components or libraries, you should also write integration tests (IT).

SkyWalking leverages the plugin `maven-surefire-plugin` to run the UTs and uses `maven-failsafe-plugin`
to run the ITs. `maven-surefire-plugin` excludes ITs (whose class name starts or ends with `*IT`, `IT*`)
and leaves them for `maven-failsafe-plugin` to run, which is bound to the `integration-test` goal.
Therefore, to run the UTs, try `./mvnw clean test`, which only runs the UTs but not the ITs.

If you would like to run the ITs, please run `./mvnw integration-test` as well as the profiles of the modules whose ITs you want to run.
If you don't want to run UTs, please add `-DskipUTs=true`.
E.g. if you would like to only run the ITs in `oap-server`, try `./mvnw -Pbackend clean verify -DskipUTs=true`,
and if you would like to run all the ITs, simply run `./mvnw clean integration-test -DskipUTs=true`.

Please be advised that if you're writing integration tests, name it with the pattern `IT*` or `*IT` so they would only run in goal `integration-test`.
