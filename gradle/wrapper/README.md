# Gradle wrapper

The official Gradle 9.6.0 wrapper has been generated and verified by successful builds.
The wrapper properties pin the distribution SHA-256. Keep `gradlew`, `gradlew.bat`,
`gradle-wrapper.jar` and `gradle-wrapper.properties` in source control.

`scripts/bootstrap-gradle.sh` is for bootstrapping an absent wrapper; it exits when
one already exists. Deliberate upgrades use Gradle's wrapper task and require a
reviewed toolchain change. Use JDK 17 for this project.
