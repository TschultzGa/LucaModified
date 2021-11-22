import Gestures from '../../helpers/Gestures';
import { searchElementBuilder } from '../../helpers/Utils';
class CheckOut {
    get alertButtonOk ():WebdriverIO.Element {return $('[id="android:id/button1"]');}
    get alertCheckoutMessage():WebdriverIO.Element {return $('[id="android:id/message"]');}
    get alertButtonCancel ():WebdriverIO.Element {return $('[id="android:id/button2"]');}
    get alertTitle ():WebdriverIO.Element {return $(searchElementBuilder('alertTitle'));}
    get slideCheckout ():WebdriverIO.Element {return $(searchElementBuilder('slideToActView'));}
    get addChild ():WebdriverIO.Element {return $(searchElementBuilder('childAddingIconImageView'));}
    checkout(){
        Gestures.swipeElementRight(this.slideCheckout);
    }
}
export default new CheckOut();