import { register, sauceLabsImageInjection } from '../helpers/Utils';
import Checkin from '../screenObjects/checkIn/CheckIn.Screen';
import Checkout from '../screenObjects/checkIn/Checkout.Screen';
import privateMeeting from '../screenObjects/checkIn/PrivateMeeting.Screen';
import Menu from '../screenObjects/Menu.Component';
import { MY_QR_CODE_TEXT, MY_QR_CODE_TITLE, ALERT_PRIVATE_MEETING_HEADER, ALERT_PRIVATE_MEETING_TEXT, PRIVATE_MEETING_TEXT, PRIVATE_MEETING_HEADER, EMPTY_IMAGE } from '../helpers/Constants';

describe('As a user I want to to be able do access check-in Functionality’s ', () => {
    beforeAll(() => {
        register();
    });
    describe('As a user I want to to be able to checkIn', () => {
        it('should be able to access checkIn screen', () => {
            Menu.checkIn.click();
            expect(Checkin.headerText).toBeDisplayed();
        });
        it('should be able to check-in', () => {
            Checkin.checkIn();
            expect(Checkout.slideCheckout).toBeDisplayed();
        });
        it('should display message when you try to check-out before 2 minutes', () => {
            driver.pause(2000);
            Checkout.checkout();
            expect(Checkout.alertTitle).toBeDisplayed();
            Checkout.alertButtonOk.click();
            expect(Checkout.slideCheckout).toBeDisplayed();
            sauceLabsImageInjection(EMPTY_IMAGE);
        });
        it('should be able to checkout after 2 minutes', () => {
            driver.pause(60000);
            expect(Checkout.slideCheckout).toBeDisplayed();
            driver.pause(60000);
            Checkout.checkout();
            expect(Checkin.headerText).toBeDisplayed();
            Menu.myLucaIcon.click();
            Menu.checkIn.click();
        });
    });
    describe('As a user I want to to be able to display my QRCode', () => {
        it('should be able display my QR code', () => {
            Checkin.showQrCodeButton.click();
            expect(Checkin.myQrCodeHeader).toHaveText(MY_QR_CODE_TITLE);
            expect(Checkin.myQrCodeText).toHaveText(MY_QR_CODE_TEXT);
            expect(Checkin.myQrCodeImage).toBeDisplayed();
        });
        it('should be able to return to check-in screen', () => {
            driver.back();
            expect(Checkin.headerText).toBeDisplayed();
        });
    });
    describe('As a user I want to to be able to create a privet meeting ', () => {
        it('When I click on create private meeting a message should be displayed', () => {
            Checkin.createPrivateMeetingButton.click();
            expect(Checkin.alertHeader).toHaveText(ALERT_PRIVATE_MEETING_HEADER);
            expect(Checkin.alertText).toHaveTextContaining(ALERT_PRIVATE_MEETING_TEXT);
        });
        it('When I accept the message the meeting will start', () => {
            Checkin.alertButtonOk.click();
            expect(privateMeeting.privateMeetingHeader).toHaveTextContaining(PRIVATE_MEETING_HEADER);
            expect(privateMeeting.endMeetingSlider).toBeDisplayed();
        });
        it('I should be to end the meeting', () => {
            privateMeeting.endMeeting();
            expect(Checkin.headerText).toBeDisplayed();
        });
    });
});
