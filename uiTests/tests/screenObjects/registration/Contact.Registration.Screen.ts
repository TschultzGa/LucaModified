import { searchElementBuilder } from '../../helpers/Utils';
class ContactData {
    get phoneNumberEditText ():WebdriverIO.Element {return $(searchElementBuilder('phoneNumberEditText'));}
    get emailEditText ():WebdriverIO.Element {return $(searchElementBuilder('emailEditText'));}
    get registrationActionButton ():WebdriverIO.Element {return $(searchElementBuilder('registrationActionButton'));}

    submitForm(phone:string, email:string) {
        this.phoneNumberEditText.setValue(phone);
        this.emailEditText.setValue(email);
        this.registrationActionButton.click();
    }
}
export default new ContactData();

