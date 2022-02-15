import { searchElementBuilder } from '../../helpers/Utils';
import Gestures from '../../helpers/Gestures'

class AccountMenu {
    get editContactButton ():WebdriverIO.Element {return $(searchElementBuilder('editContactDataItem'));};
    get deleteAccountButton ():WebdriverIO.Element {return $(searchElementBuilder('deleteAccountItem'));};
    get faqButton ():WebdriverIO.Element {return $(searchElementBuilder('faqTextView'));};
    get supportButton ():WebdriverIO.Element {return $(searchElementBuilder('supportTextView'));};
    get alertButtonOk():WebdriverIO.Element {return $('[id="android:id/button1"]');};
    get alertText():WebdriverIO.Element {return $('[id="android:id/message"]');};
    get dataRequestButton () :WebdriverIO.Element {return $(searchElementBuilder('dataRequestTextView'));};
    get privacyTextButton():WebdriverIO.Element {return $(searchElementBuilder('privacyTextView'));};
    get termsOfUseButton():WebdriverIO.Element {return $(searchElementBuilder('termsTextView'));};
    get imprintButton():WebdriverIO.Element {return $(searchElementBuilder('imprintTextView'));};
    get healthDepartmentKeyButton():WebdriverIO.Element {return $(searchElementBuilder('healthDepartmentKeyTextView'));};
    get showAppDataButton():WebdriverIO.Element {return $(searchElementBuilder('showAppDataTextView'));};
    get versionButton():WebdriverIO.Element {return $(searchElementBuilder('versionTextView'));};
    get gitlabButton():WebdriverIO.Element {return $(searchElementBuilder('gitlabTextView'));};

    deleteUser(){
        expect(this.editContactButton).toBeDisplayed();
        Gestures.swipeUp();
        this.deleteAccountButton.click();
        this.alertButtonOk.click();
    }
}
export default new AccountMenu();








