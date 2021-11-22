import { openDeepLink, searchElementBuilder,sauceLabsImageInjection, getCheckinQrDeepLinkByEnv } from '../../helpers/Utils';
import { VALID_QR_LOCATION_IMAGE } from '../../helpers/Constants';

class CheckIn {
    get cameraEnable ():WebdriverIO.Element {return $(searchElementBuilder('requestCameraPermissionImageView'));}
    get headerText():WebdriverIO.Element {return $(searchElementBuilder('headingTextView'));}
    get showQrCodeButton():WebdriverIO.Element {return $(searchElementBuilder('showQrCodeButton'));}
    get createPrivateMeetingButton():WebdriverIO.Element {return $(searchElementBuilder('createMeetingButton'));}
    get historyButton():WebdriverIO.Element {return $(searchElementBuilder('historyTextView'));}
    get myQrCodeHeader():WebdriverIO.Element {return $(searchElementBuilder('myQrCodeTextView'));}
    get myQrCodeText():WebdriverIO.Element {return $(searchElementBuilder('myQrCodeDescriptionTextView'));}
    get myQrCodeImage():WebdriverIO.Element {return $(searchElementBuilder('qrCodeImageView'));}
    get alertButtonOk():WebdriverIO.Element {return $('[id="android:id/button1"]');}
    get alertButtonCancel():WebdriverIO.Element {return $('[id="android:id/button2"]');}
    get alertHeader():WebdriverIO.Element {return $(searchElementBuilder('alertTitle'));}
    get alertText():WebdriverIO.Element {return $('[id="android:id/message"]');}
    get cameraPermissionOld():WebdriverIO.Element {return $('[id="com.android.packageinstaller:id/permission_allow_button"]');}
    get cameraPermissionNew():WebdriverIO.Element {return $('[id="com.android.permissioncontroller:id/permission_allow_foreground_only_button"]');}
    checkIn(){
        //For sauce Labs execution
        sauceLabsImageInjection(VALID_QR_LOCATION_IMAGE);
        this.cameraEnable.click();
        this.alertButtonOk.click();
        if ( this.cameraPermissionNew.isExisting() ){
            this.cameraPermissionNew.click();
        } else {
            this.cameraPermissionOld.click();
        }
        driver.pause(3000);
        openDeepLink(getCheckinQrDeepLinkByEnv());
        this.alertButtonOk.waitForExist({ timeout: 10000 });
        this.alertButtonOk.click();
    }
}
export default new CheckIn();

