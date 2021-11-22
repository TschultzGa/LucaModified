import config from './wdio.shared.sauce.conf';

// ============
// Specs
// ============
config.specs = [
  './tests/specs/app.*.spec.ts',
];
// ============
// Capabilities
// ============
// For all capabilities please check
// http://appium.io/docs/en/writing-running-appium/caps/#general-capabilities

config.capabilities = [
  {
    // For more information about the supported Sauce Labs capabilities see:
    // https://wiki.saucelabs.com/display/DOCS/Appium+Capabilities+for+Real+Device+Testing
    // Sauce Labs RDC is not yet W3C compatible, that's why we use ts-ignore
    // @ts-ignore
    platformName: 'Android',
    platformVersion: '11',
    orientation: 'PORTRAIT',
    phoneOnly: true,
    automationName: 'UiAutomator2',
    // Keep the device connected between tests so we don't need to wait for the cleaning process
    cacheId: 'jsy1v49pn9',
    newCommandTimeout: 2400,
    // Always default the language to a language you prefer so you know the 
    // app language is always as expected
    language: 'en',
    locale: 'en',
    // Enable image-injection on RDC
    sauceLabsImageInjectionEnabled: true,
    commandTimeout:600,
    idleTimeout: 900,   
    // The path to the app that has been uploaded to the Sauce Storage,
    // see https://wiki.saucelabs.com/display/DOCS/Application+Storage for more information
    app: 'storage:filename='+ process.env.APPNAME,
    appWaitActivity: 'de.culture4life.luca.*',
    // Add a name to the test
    name: 'Test',
    build: `Android Real Devices: ${new Date().getTime()}`
  },
];

// This port was defined in the `wdio.shared.conf.ts`
delete config.port;

exports.config = config;
