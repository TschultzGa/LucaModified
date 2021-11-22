import { searchElementBuilder } from '../../helpers/Utils';
class AddressRegistration {
    get streetEditText ():WebdriverIO.Element {return $(searchElementBuilder('streetEditText'));}
    get NumberEditText ():WebdriverIO.Element {return $(searchElementBuilder('houseNumberEditText'));}
    get postalCodeEditText ():WebdriverIO.Element {return $(searchElementBuilder('postalCodeEditText'));}
    get cityEditText ():WebdriverIO.Element {return $(searchElementBuilder('cityNameEditText'));}
    get confirmationButton ():WebdriverIO.Element {return $(searchElementBuilder('registrationActionButton'));}
    get headerText () :WebdriverIO.Element {return $(searchElementBuilder('registrationHeading'));}

    submitForm(street:string, number:string, postalCode:string, city:string) {
        this.streetEditText.setValue(street);
        this.NumberEditText.setValue(number);
        this.postalCodeEditText.setValue(postalCode);
        this.cityEditText.setValue(city);
        this.confirmationButton.click();
    }
}
export default new AddressRegistration();