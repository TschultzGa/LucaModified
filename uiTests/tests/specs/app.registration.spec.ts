import TermsOfUseScreen from '../screenObjects/registration/TermsOfUse.Registration.Screen';
import ContactAgreementScreen from '../screenObjects/registration/ContactAgreement.Registration.Screen';
import NameScreen from '../screenObjects/registration/Name.Registration.Screen';
import ContactScreen from '../screenObjects/registration/Contact.Registration.Screen';
import AddressScreen from '../screenObjects/registration/Address.Registration.Screen';
import ConfirmScreen from '../screenObjects/registration/Confirmation.Registration.Screen';
import Menu from '../screenObjects/Menu.Component';
import { VALID_PHONE, VALID_EMAIL, VALID_NAME, VALID_SURNAME, VALID_STREET, VALID_NUMBER, VALID_POSTAL_CODE, VALID_POSTAL_CITY } from '../helpers/Constants';

describe('As a user I want to to be able to register my user', () => {
    it('should be able to accept the terms and conditions', () => {
        TermsOfUseScreen.submitForm();
        expect(ContactAgreementScreen.descriptionTextView).toBeDisplayed();
    });
    it('should display contact data police and continue', ()=>{
        ContactAgreementScreen.primaryActionButton.click();
        expect(NameScreen.firstNameText).toBeDisplayed();
    });
    it('should be able to insert name and last and click on continue', ()=>{
        NameScreen.submitForm(VALID_NAME, VALID_SURNAME);
        expect(ContactScreen.phoneNumberEditText).toBeDisplayed();
    });
    it('should be able to insert phone number, email and click continue', ()=>{
        ContactScreen.submitForm(VALID_PHONE, VALID_EMAIL);
        expect(AddressScreen.headerText).toBeDisplayed();
    });
    it('should be able to insert address information and click continue', ()=>{
        AddressScreen.submitForm(VALID_STREET, VALID_NUMBER, VALID_POSTAL_CODE, VALID_POSTAL_CITY);
        expect(ConfirmScreen.descriptionTextView).toBeDisplayed();
    });
    it('should confirm registration', ()=>{
        ConfirmScreen.confirm();
        expect(Menu.checkIn).toBeDisplayed();
    });
});
