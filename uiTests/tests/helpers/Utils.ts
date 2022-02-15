import TermsOfUseScreen from '../screenObjects/registration/TermsOfUse.Registration.Screen';
import ContactAgreementScreen from '../screenObjects/registration/ContactAgreement.Registration.Screen';
import NameScreen from '../screenObjects/registration/Name.Registration.Screen';
import ContactScreen from '../screenObjects/registration/Contact.Registration.Screen';
import AddressScreen from '../screenObjects/registration/Address.Registration.Screen';
import ConfirmScreen from '../screenObjects/registration/Confirmation.Registration.Screen';
import AccountMenuScreen from '../screenObjects/account/AccountMenu.Screen';
import MenuComponent from '../screenObjects/Menu.Component';


import {
  VALID_PHONE, VALID_EMAIL, VALID_NAME, VALID_SURNAME, VALID_STREET, VALID_NUMBER,
  VALID_POSTAL_CODE, VALID_POSTAL_CITY, QS_QR_DEEPLINK, RELEASE_QR_DEEPLINK, CURRENT_DATE,
} from './Constants';
import AndroidSettings from '../screenObjects/AndroidSettings';
import AccountScreen from '../screenObjects/account/Account.Screen';
import FilesScreen from '../screenObjects/Files.Screen';

const { readFileSync } = require('fs');
const { join } = require('path');
/**
 * Register new User, common function.
*/
export function register() {
  TermsOfUseScreen.submitForm();
  ContactAgreementScreen.primaryActionButton.click();
  NameScreen.submitForm(VALID_NAME, VALID_SURNAME);
  ContactScreen.submitForm(VALID_PHONE, VALID_EMAIL);
  AddressScreen.submitForm(VALID_STREET, VALID_NUMBER, VALID_POSTAL_CODE, VALID_POSTAL_CITY);
  ConfirmScreen.confirm();
}
/**
 * Open Deep link based on URL provided
*/
export function openDeepLink(url:String) {
  if (process.env.ENV == 'local') {
    return driver.execute('mobile:deepLink', {
      url,
      package: `de.culture4life.luca.${process.env.BUILD}`,
    });
  }
}
/**
 * Open Deep link based on URL provided
*/
export function sauceLabsImageInjection(imageName:String) {
  if (process.env.ENV == 'sauce') {
    const qrCodeImage = readFileSync(join(process.cwd(), `qrCodesImages/${process.env.BUILD}_${imageName}`), 'base64');
    driver.execute(`sauce:inject-image=${qrCodeImage}`);
  }
}
/**
 * Return String for element search by Build type.
*/
export function searchElementBuilder(element:String) {
  return `[id="de.culture4life.luca.${process.env.BUILD}:id/${element}"]`;
}
export function getCheckinQrDeepLinkByEnv():String {
  let deepLink = '';
  if (process.env.BUILD == 'qs') {
    deepLink = QS_QR_DEEPLINK;
  }
  if (process.env.BUILD == 'release') {
    deepLink = RELEASE_QR_DEEPLINK;
  }
  return deepLink;
}

export function addTimestampToName(nameOfTheFile: string) {
  if (FilesScreen.documentTitles.length >= 1) {
    FilesScreen.documentTitles.forEach(element => {
                  if (element.getText().match(nameOfTheFile)) {
                          element.setValue(createNameWithTimestamp(nameOfTheFile));
                  }
          });
  } else {
          console.log('File manager is not opened')
  }
}

export function createNameWithTimestamp(nameOfTheFile: string) {
  var tempName = nameOfTheFile.slice(0, nameOfTheFile.length - 4);
  tempName = tempName.concat(CURRENT_DATE.toString());
  tempName = tempName.concat('.txt');
  return tempName;
}

export function createFilepathWithTimestamp(folderPath: string, nameOfTheFile: string) { return folderPath.concat(createNameWithTimestamp(nameOfTheFile)); }
/**
 * Delete user to ensure reusability of test data
*/
export function deleteUser(){
  MenuComponent.account.click();
  AccountMenuScreen.deleteUser();
  driver.pause(1000);
}
