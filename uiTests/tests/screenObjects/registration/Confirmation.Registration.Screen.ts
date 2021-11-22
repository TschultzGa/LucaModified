import { searchElementBuilder } from '../../helpers/Utils';
class RegistrationConfirmation {
    get descriptionTextView ():WebdriverIO.Element {return $(searchElementBuilder('descriptionTextView'));}
    get headingTextView ():WebdriverIO.Element {return $(searchElementBuilder('headingTextView'));}
    get primaryActionButton ():WebdriverIO.Element {return $(searchElementBuilder('primaryActionButton'));}

    confirm(){
        this.primaryActionButton.click();
    }
}
export default new RegistrationConfirmation();