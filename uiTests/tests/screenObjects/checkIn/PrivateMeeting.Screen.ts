import { searchElementBuilder } from '../../helpers/Utils';
import Gestures from '../../helpers/Gestures';
class PrivateMeeting {
    get okAlertButton ():WebdriverIO.Element {return $('[id="android:id/button1"]');}
    get privateMeetingHeader ():WebdriverIO.Element {return $(searchElementBuilder('headingTextView'));}
    get privateMeetingDurationText ():WebdriverIO.Element {return $(searchElementBuilder('durationHeadingTextView'));}
    get privateMeetingQrCodeImage ():WebdriverIO.Element {return $(searchElementBuilder('qrCodeImageView'));}
    get privateMeetingText():WebdriverIO.Element {return $(searchElementBuilder('subHeadingTextView"]'));}
    get endMeetingSlider ():WebdriverIO.Element {return $(searchElementBuilder('slideToActView'));}
    endMeeting(){
        Gestures.swipeElementRight(this.endMeetingSlider);
    }
}
export default new PrivateMeeting();
