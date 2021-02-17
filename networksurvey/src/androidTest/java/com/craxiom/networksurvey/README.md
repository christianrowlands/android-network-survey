# Android UI Tests
This directory contains the Android connected tests.  Tests are to be executed against an Emulator
instance or a hardware device.  Tests which require a hardware device should be annotated with the
`@RequiresDevice` annotation.  Those tests will automatically not be included when test runs target
an emulator.

## Running Tests
Tests should be executed via gradle using the following
```bash
./gradlew dockerComposeUp connectedAndroidTest
```

When tests are complete, you can chose to shut down the docker container or leave it running for 
future runs
```bash
./gradlew dockerComposeDown
```
