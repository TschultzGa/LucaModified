import TermsOfUseScreen from '../screenObjects/registration/TermsOfUse.Registration.Screen';
import ContactAgreementScreen from '../screenObjects/registration/ContactAgreement.Registration.Screen';
import NameScreen from '../screenObjects/registration/Name.Registration.Screen';
import Contact from '../screenObjects/registration/Contact.Registration.Screen';
import Address from '../screenObjects/registration/Address.Registration.Screen';
import Confirm from '../screenObjects/registration/Confirmation.Registration.Screen';
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
        expect(Contact.phoneNumberEditText).toBeDisplayed();
    });
    it('should be able to insert phone number, email and click continue', ()=>{
        Contact.submitForm(VALID_PHONE, VALID_EMAIL);
        expect(Address.headerText).toBeDisplayed();
    });
    it('should be able to insert address information and click continue', ()=>{
        Address.submitForm(VALID_STREET, VALID_NUMBER, VALID_POSTAL_CODE, VALID_POSTAL_CITY);
        expect(Confirm.descriptionTextView).toBeDisplayed();
    });
    it('should confirm registration', ()=>{
        Confirm.confirm();
        expect(Menu.checkIn).toBeDisplayed();
    });
});
