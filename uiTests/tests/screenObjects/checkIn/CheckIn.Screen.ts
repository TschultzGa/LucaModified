import { openDeepLink, searchElementBuilder,sauceLabsImageInjection, getCheckinQrDeepLinkByEnv } from '../../helpers/Utils';
import { VALID_QR_LOCATION_IMAGE } from '../../helpers/Constants';

class CheckIn {
    get cameraEnable ():WebdriverIO.Element {return $(searchElementBuilder('startCameraImageView'));}
    get headerText():WebdriverIO.Element {return $(searchElementBuilder('actionBarTitleTextView'));}
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
    get cameraPermissionNew():WebdriverIO.Element {return $('[id="com.android.permissioncontroller:id/permission_allow_button"]');}
    get cameraPermission():WebdriverIO.Element {return $('[id="com.android.permissioncontroller:id/permission_allow_foreground_only_button"]');}
    get checkinConfrimationHeaderText():WebdriverIO.Element {return $(searchElementBuilder('checkInHeaderTextView'));}
    get checkinConfrimationDescriptionText():WebdriverIO.Element {return $(searchElementBuilder('checkInDescriptionTextView'));}
    get actionButton():WebdriverIO.Element {return $(searchElementBuilder('actionButton'));}
    checkIn(){
        //For sauce Labs execution
        sauceLabsImageInjection(VALID_QR_LOCATION_IMAGE);
        this.cameraEnable.click();
        this.alertButtonOk.click();
        driver.pause(2000);
        if ( this.cameraPermissionNew.isExisting() ){
            this.cameraPermissionNew.click();
        }
        if(this.cameraPermissionOld.isExisting()){
            this.cameraPermissionOld.click();
        }
        if(this.cameraPermission.isExisting()){
            this.cameraPermission.click();
        }
        driver.pause(3000);
        openDeepLink(getCheckinQrDeepLinkByEnv());
        this.actionButton.waitForExist({ timeout: 10000 });
        this.actionButton.click();
    }
}
export default new CheckIn();

