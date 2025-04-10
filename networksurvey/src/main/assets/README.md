The company identifiers provided in the company_identifiers.yaml are pulled from: https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/uuids/service_class.yaml

To download the latest version of that file and package it with this app, run the following command:

```bash
./gradlew downloadBluetoothCompanyIdentifiers
```

The same is true for the member_uuids.yaml file, which is pulled from: https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/uuids/service_class.yaml

To download the latest version of that file and package it with this app, run the following command:

```bash
./gradlew downloadBluetoothMemberUuid
```
