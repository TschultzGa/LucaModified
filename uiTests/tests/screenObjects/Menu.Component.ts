import { searchElementBuilder } from '../helpers/Utils';
class MenuComponent {
    get myLucaIcon ():WebdriverIO.Element {return $(searchElementBuilder('myLucaFragment'));}
    get account ():WebdriverIO.Element {return $(searchElementBuilder('accountFragment'));}
    get checkIn ():WebdriverIO.Element {return $(searchElementBuilder('checkInFragment'));}
}
export default new MenuComponent();