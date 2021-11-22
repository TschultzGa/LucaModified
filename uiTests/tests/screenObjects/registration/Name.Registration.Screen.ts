import { searchElementBuilder } from '../../helpers/Utils';
class Details {
    get lastNameText ():WebdriverIO.Element {return $(searchElementBuilder('firstNameEditText'));}
    get firstNameText ():WebdriverIO.Element {return $(searchElementBuilder('lastNameEditText'));}
    get continueButton ():WebdriverIO.Element {return $(searchElementBuilder('registrationActionButton'));}

    submitForm(firstName:string, lastName:string) {
        this.firstNameText.setValue(firstName);
        this.lastNameText.setValue(lastName);
        this.continueButton.click();
    }
}

export default new Details();
