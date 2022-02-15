import { searchElementBuilder } from '../../helpers/Utils';
import Gestures from '../../helpers/Gestures';
import { EDIT_EMAIL, EDIT_NAME, EDIT_NUMBER, EDIT_PHONE, EDIT_POSTAL_CITY, EDIT_POSTAL_CODE, EDIT_STREET, EDIT_SURNAME } from '../../helpers/Constants';

class EditContactData {
get headerText():WebdriverIO.Element {return $(searchElementBuilder('registrationHeading'));}
get firstNameEditText():WebdriverIO.Element {return $(searchElementBuilder('firstNameEditText'));}
get lastNameEditText():WebdriverIO.Element {return $(searchElementBuilder('lastNameEditText'));}
get phoneNumberEditText():WebdriverIO.Element {return $(searchElementBuilder('phoneNumberEditText'));}
get emailEditText():WebdriverIO.Element {return $(searchElementBuilder('emailEditText'));}
get streetEditText():WebdriverIO.Element {return $(searchElementBuilder('streetEditText'));}
get houseNumberEditText():WebdriverIO.Element {return $(searchElementBuilder('houseNumberEditText'));}
get postalCodeEditText():WebdriverIO.Element {return $(searchElementBuilder('postalCodeEditText'));}
get cityNameEditText():WebdriverIO.Element {return $(searchElementBuilder('cityNameEditText'));}
get updateButton():WebdriverIO.Element {return $(searchElementBuilder('registrationActionButton'));}
get textInputErrors():WebdriverIO.ElementArray { return $$(searchElementBuilder('textinput_error'))};
get alert():WebdriverIO.Element {return $(searchElementBuilder('alertTitle'));}
get okButton():WebdriverIO.Element {return $('[id="android:id/button1"]');}

    editData(firstName:string, lastName:string, phone:string, email:string, street:string, number:string, postalCode:string, city:string) {
        this.firstNameEditText.setValue(firstName);
        this.lastNameEditText.setValue(lastName);
        this.phoneNumberEditText.setValue(phone);
        this.emailEditText.setValue(email);
        Gestures.swipeUp();
        this.streetEditText.setValue(street);
        this.houseNumberEditText.setValue(number);
        this.postalCodeEditText.setValue(postalCode);
        this.cityNameEditText.setValue(city);
        this.updateButton.click();
    }

    verifyData(firstName:string, lastName:string, phone:string, email:string, street:string, number:string, postalCode:string, city:string) {
        expect(this.firstNameEditText.getText()).toEqual(firstName);
        expect(this.lastNameEditText.getText()).toEqual(lastName);
        expect(this.phoneNumberEditText.getText()).toEqual(phone);
        expect(this.emailEditText.getText()).toEqual(email);
        Gestures.swipeUp();
        expect(this.streetEditText.getText()).toEqual(street);
        expect(this.houseNumberEditText.getText()).toEqual(number);
        expect(this.postalCodeEditText.getText()).toEqual(postalCode);
        expect(this.cityNameEditText.getText()).toEqual(city);
    }

    clearData() {
        Gestures.swipeUp();
        this.cityNameEditText.clearValue();
        this.cityNameEditText.click();

        this.postalCodeEditText.clearValue();
        this.postalCodeEditText.click();

        this.houseNumberEditText.clearValue();
        this.houseNumberEditText.click();

        this.streetEditText.clearValue();
        this.streetEditText.click();

        this.emailEditText.clearValue();

        Gestures.swipeDown();
        
        this.phoneNumberEditText.clearValue();
        this.phoneNumberEditText.click();

        this.lastNameEditText.clearValue();
        this.lastNameEditText.click();

        this.firstNameEditText.clearValue();
        this.firstNameEditText.click();

        driver.pause(1000);
        this.emailEditText.click();
        driver.hideKeyboard();
        Gestures.swipeUp();
    }


}

export default new EditContactData();

