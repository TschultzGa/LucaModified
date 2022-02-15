import { CONTACT_TRACING_FILENAME, COVID_CERTIFICATE_FILENAME, CURRENT_DATE, DAILY_KEY_FILENAME, DOWNLOAD_FILEPATH, } from '../helpers/Constants';
import { createFilepathWithTimestamp, searchElementBuilder } from '../helpers/Utils';
import AccountScreen from './account/Account.Screen';
import AndroidSettings from './AndroidSettings';

class Files {
get deleteButton():WebdriverIO.Element {return $('~Delete');}
get okButton():WebdriverIO.Element {return $('[id="android:id/button1"]');}
get message():WebdriverIO.Element {return $('[id="com.android.documentsui:id/message"]');}
get showRoots():WebdriverIO.Element {return $('~Show roots');}
get moreOptions():WebdriverIO.Element {return $('~More options');}
get documentTitles():WebdriverIO.ElementArray {return $$('[id="android:id/title"]');}

verifyFilesCreated() {
        let COVID_CERTIFICATE_FILE = driver.pullFile(createFilepathWithTimestamp(DOWNLOAD_FILEPATH, COVID_CERTIFICATE_FILENAME));
        let DAILY_KEY_FILE = driver.pullFile(createFilepathWithTimestamp(DOWNLOAD_FILEPATH, DAILY_KEY_FILENAME));
        let CONTACT_TRACING_FILE = driver.pullFile(createFilepathWithTimestamp(DOWNLOAD_FILEPATH, CONTACT_TRACING_FILENAME));
}
}

export default new Files();

