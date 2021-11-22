import { searchElementBuilder } from '../helpers/Utils';
class MyLuca {
    get addChildButton ():WebdriverIO.Element {return $(searchElementBuilder('childAddingIconImageView'));}
    get bookAppointmentButton ():WebdriverIO.Element {return $(searchElementBuilder('bookAppointmentImageView'));}
    get myLucaIcon ():WebdriverIO.Element {return $(searchElementBuilder('myLucaFragment'));}
    get historyIcon ():WebdriverIO.Element {return $(searchElementBuilder('historyFragment'));}
    get account ():WebdriverIO.Element {return $(searchElementBuilder('accountFragment'));}
    get checkIn ():WebdriverIO.Element {return $(searchElementBuilder('qrCodeFragment'));}
}
export default new MyLuca();