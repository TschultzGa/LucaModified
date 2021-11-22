## Installation

1. Clone the repository `git clone https://gitlab.com/lucaapp/android.git`
1. Install dependencies `npm install`
4. Create an `./apps` directory and download the app files into it
5. Open your simulator
6. Configure capabilities in [`wdio.android.app.conf.ts`](./config/wdio.android.app.conf.ts)
5. Run tests locally `npm run android.app.release`

## Configuration files

[`wdio.shared.conf.ts`](./config/wdio.shared.conf.ts) holds **all the defaults** so Android configs only need to hold the capabilities and specs that are needed.

Please check [`wdio.shared.conf.ts`](./config/wdio.shared.conf.ts) for the minimal configuration options.
Notes are added for why a different value has been selected in comparison to the default values that WebdriverIO provides.

### Execution

You can run tests with the following command:

    // for local execution
    npm run android.app.<qs|release> -- --spec=tests/specs/app.checkin.spec.ts

## Cloud vendors

### Sauce Labs

#### Upload apps to Sauce Storage

If you want to use Android emulators or real Android devices in the Sauce Labs UI, you need to upload the apps to the Sauce Labs Storage.
You can do so by executing the following commands from the root of this project:

    cd scripts
    ./push_apps_to_sauce_storage.sh

#### Run app tests on the Sauce Labs Real Device Cloud

Please check [wdio.android.app.conf.ts](./config/wdio.android.app.conf.ts) to see the setup for real Android devices.

You can use the following command to execute the tests in the cloud (see [`package.json`](./package.json)):

    // for real Android devices on EU DC
    npm run android.sauce.rdc.app.eu.release --USER=<your_user> --ACCESSKEY=<your_access_key> --APPNAME=<app_name>

#### Run app tests on the Sauce Labs Emulators and Simulators

Please check [wdio.android.emulators.app.conf.ts](./config/saucelabs/wdio.android.emulators.app.conf.ts) to see the setup for Android emulators.

You can use the following commands to execute the tests in the cloud (see [`package.json`](./package.json)):

    // for Android emulators on EU DC
    npm run android.sauce.emulators.app.eu -- --USER=<your_user> --ACCESSKEY=<your_access_key> --APPNAME=<app_name>
    
    // for real Android devices on EU DC
    npm run android.sauce.rdc.app.eu -- --USER=<your_user> --ACCESSKEY=<your_access_key> --APPNAME=<app_name>

## Dependencies

The tests are currently based on:

- WebdriverIO: `7.##.#`
- Appium: `1.20.#`