import { searchElementBuilder, openDeepLink, sauceLabsImageInjection } from '../helpers/Utils';
import AndroidSettings from './AndroidSettings'
import Gestures from '../helpers/Gestures';
import {FIRST_COVID_CERTIFICATE_TEXT,COMPLETE_COVID_CERTIFICATE_TEXT    } from '../helpers/Constants'
class MyLuca {
    get addChildButton ():WebdriverIO.Element {return $(searchElementBuilder('childrenActionBarMenuImageView'));};
    get bookAppointmentButton ():WebdriverIO.Element {return $(searchElementBuilder('appointmentsActionBarMenuImageView'));};
    get myLucaIcon ():WebdriverIO.Element {return $(searchElementBuilder('myLucaFragment'));};
    get cameraPermissionOld():WebdriverIO.Element {return $('[id="com.android.packageinstaller:id/permission_allow_button"]');};
    get cameraPermissionNew():WebdriverIO.Element {return $('[id="com.android.permissioncontroller:id/permission_allow_foreground_only_button"]');};
    get alertButtonOk():WebdriverIO.Element {return $('[id="android:id/button1"]');};
    get alertHeader():WebdriverIO.Element {return $(searchElementBuilder('alertTitle'));};
    get alertText():WebdriverIO.Element {return $('[id="android:id/message"]');};
    get myLucaUserName():WebdriverIO.Element {return $(searchElementBuilder('personNameTextView'));};
    get cardCollapse():WebdriverIO.Element {return $(searchElementBuilder('collapseIndicator'));};
    get qrCodeImage():WebdriverIO.Element {return $(searchElementBuilder('qrCodeImageView'));};
    get deleteCard():WebdriverIO.Element {return $(searchElementBuilder('deleteItemButton'));};
    get addDocumentButton():WebdriverIO.Element {return $(searchElementBuilder('primaryActionButton'));};
    get myLucaDescriptionText():WebdriverIO.Element {return $(searchElementBuilder('emptyDescriptionTextView'));};
    get myLucaTitleText():WebdriverIO.Element {return $(searchElementBuilder('actionBarTitleTextView'));};
    get myLucaDescriptionTitleText():WebdriverIO.Element {return $(searchElementBuilder('emptyTitleTextView'));};
    get pageIndicator() :WebdriverIO.Element {return $(searchElementBuilder('myLucaItemsViewPagerIndicator'));};
    get documentCard() :WebdriverIO.Element {return $(searchElementBuilder('cardView'));};
    get firstCovidCertificateText ():WebdriverIO.Element {return $(AndroidSettings.findAndroidElementByText(FIRST_COVID_CERTIFICATE_TEXT));};
    get CompleteCovidCertificateText ():WebdriverIO.Element {return $(AndroidSettings.findAndroidElementByText(COMPLETE_COVID_CERTIFICATE_TEXT));};

    addDocument(qrCode:String, deepLink:String){
        //For sauce Labs execution
        sauceLabsImageInjection(qrCode);
        this.addDocumentButton.click();
        driver.pause(3000);
        openDeepLink(deepLink);
    }
    
    swipeCard(){
        Gestures.swipeElementRight(this.documentCard);
    }

    deleteDocument(){
        this.cardCollapse.click();
        Gestures.swipeElementUp(this.documentCard)
        this.deleteCard.click();
    }
}
export default new MyLuca();