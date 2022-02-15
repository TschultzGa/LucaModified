import { searchElementBuilder } from '../../helpers/Utils';
import AndroidSettings from '../AndroidSettings';


class Account {
get headerText():WebdriverIO.Element {return $(searchElementBuilder('actionBarTitleTextView'));}

get editContactDataButton():WebdriverIO.Element {return $(searchElementBuilder('editContactDataItem'));}

get newsButton():WebdriverIO.Element {return $(searchElementBuilder('guidesItem'));}

get faqButton():WebdriverIO.Element {return $(searchElementBuilder('faqItem'));}
get supportButton():WebdriverIO.Element {return $(searchElementBuilder('supportItem'));}

get requestDataButton():WebdriverIO.Element {return $(searchElementBuilder('dataRequestItem'));}
get dataProtectionButton():WebdriverIO.Element {return $(searchElementBuilder('dataProtectionItem'));}
get termsAndConditionsButton():WebdriverIO.Element {return $(searchElementBuilder('termsItem'));}
get imprintButton():WebdriverIO.Element {return $(searchElementBuilder('imprintItem'));}
get dailyKeyButton():WebdriverIO.Element {return $(searchElementBuilder('dailyKeyItem'));}
get appVersionButton():WebdriverIO.Element {return $(searchElementBuilder('versionItem'));}
get appDataButton():WebdriverIO.Element {return $(searchElementBuilder('appDataItem'));}
get sourceCodeButton():WebdriverIO.Element {return $(searchElementBuilder('sourceCodeItem'));}

get deleteAccountButton():WebdriverIO.Element {return $(searchElementBuilder('deleteAccountItem'));}


get requestDataDropdown():WebdriverIO.Element {return $(searchElementBuilder('title'));}

get supportMessage():WebdriverIO.Element {return $('[id="android:id/content_preview_text"]');}
get alert():WebdriverIO.Element {return $(searchElementBuilder('alertTitle'));}
get okButton():WebdriverIO.Element {return $('[id="android:id/button1"]');}
get openGitlabButton():WebdriverIO.Element {return $('[id="android:id/button1"]');}
get cancelGitlabButton():WebdriverIO.Element {return $('[id="android:id/button2"]');}

get entityHeaderTitle():WebdriverIO.Element {return $('[id="com.android.settings:id/entity_header_title"]');}

//Save requested data
get savingField():WebdriverIO.Element {return $('[id="android:id/title"]');}
get saveButton():WebdriverIO.Element {return $('[id="android:id/button1"]');}

//Daily key
get dailyKeyBackButton():WebdriverIO.Element {return $(searchElementBuilder('actionBarBackButtonImageView'));}
get dailyKeyDateText():WebdriverIO.Element {return $(searchElementBuilder('issuerValueTextView'));}
get dailyKeyIssuerText():WebdriverIO.Element {return $(searchElementBuilder('issuerValueTextView'));}
get dailyKeySignedImage():WebdriverIO.Element {return $('~Yes');}
get dailyKeydownloadCertificateButton():WebdriverIO.Element {return $(searchElementBuilder('downloadCertificateButton'));}


//Chrome url bar
get urlBar():WebdriverIO.Element {return $('[id="com.android.chrome:id/url_bar"]');}
}

export default new Account();

