import { searchElementBuilder } from '../../helpers/Utils';
class TermsOfUse {
    get termsDescriptionText ():WebdriverIO.Element {return  $('~descriptionTextView');}
    get termsCheck ():WebdriverIO.Element {return  $('~Terms of use');}
    get getStartedButton ():WebdriverIO.Element {return  $(searchElementBuilder('primaryActionButton'));}

    submitForm() {
        this.termsCheck.click();
        this.getStartedButton.click();
    }
}

export default new TermsOfUse();
