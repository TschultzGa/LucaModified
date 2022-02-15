import Menu from '../screenObjects/Menu.Component';
import { addTimestampToName, register } from '../helpers/Utils';
import Account from '../screenObjects/account/Account.Screen';
import FilesScreen from '../screenObjects/Files.Screen';
import News from '../screenObjects/account/News.Screen';
import EditContactData from '../screenObjects/account/EditContactData.Screen';
import AndroidSettings from '../screenObjects/AndroidSettings';
import { EDIT_PHONE, EDIT_EMAIL, EDIT_NAME, EDIT_SURNAME, EDIT_STREET, EDIT_NUMBER, EDIT_POSTAL_CODE, EDIT_POSTAL_CITY, NEWS_HEADER_TEXT, ACCOUNT_HEADER_TEXT, EDIT_CONTACT_DATA_HEADER_TEXT, CONTACT_TRACING_FILENAME, COVID_CERTIFICATE_FILENAME, DAILY_KEY_FILENAME, } from '../helpers/Constants';
import { FAQ, GITLAB, IMPRINT_SLOW, IMPRINT_FAST, PRIVACY_POLICY, TERMS_AND_CONDITIONS } from '../helpers/Constants';
import Gestures from '../helpers/Gestures';
import AccountScreen from '../screenObjects/account/Account.Screen';



describe('As a user I want to to be able interect with Account functionalities', () => {
    beforeAll(() => {
        register();
        Menu.account.click();
        expect(Account.headerText).toHaveText(ACCOUNT_HEADER_TEXT);
    });

    //EDIT CONTACT DATA
    describe('As a user I want to to be able to edit my contact data', () => {
        it('should be able to open contact form', () => {
            Account.editContactDataButton.click();
            expect(EditContactData.headerText).toHaveText(EDIT_CONTACT_DATA_HEADER_TEXT);
        });
        it('should be able to change my contact data', ()=>{
            EditContactData.editData(EDIT_NAME, EDIT_SURNAME, EDIT_PHONE, EDIT_EMAIL, EDIT_STREET,
                 EDIT_NUMBER, EDIT_POSTAL_CODE, EDIT_POSTAL_CITY)
                 expect(Account.headerText).toHaveText(ACCOUNT_HEADER_TEXT);
        });
        it('should be able to open contact form after updating contact data', () => {
            Account.editContactDataButton.click();
            expect(EditContactData.headerText).toHaveText(EDIT_CONTACT_DATA_HEADER_TEXT);
        });
        it('should be able to see updated data in edit contact', ()=> {
            EditContactData.verifyData(EDIT_NAME, EDIT_SURNAME, EDIT_PHONE, EDIT_EMAIL, EDIT_STREET, 
                EDIT_NUMBER, EDIT_POSTAL_CODE, EDIT_POSTAL_CITY);
        });
        })
        it('should be able to go back', ()=> {
            driver.back();
            expect(Account.headerText).toHaveText(ACCOUNT_HEADER_TEXT);
        })
    });

        describe('As a user I want to be notified about erros during edit contact', () => {
        it('should be able to open contact form', () => {
            Account.editContactDataButton.click();
            expect(EditContactData.headerText).toHaveText(EDIT_CONTACT_DATA_HEADER_TEXT);
        });
        it('should be able to clear all fields', () => {
            EditContactData.clearData();
            expect(EditContactData.textInputErrors.length).toBeGreaterThan(3);
        });
        it('should be able to see alert when updating', () => {
            EditContactData.updateButton.click();
            expect(EditContactData.alert).toBeDisplayed();
        });
        it('should be able go back without saving', () => {
            EditContactData.okButton.click();
            driver.pause(1000);
            driver.back();
            expect(Account.headerText).toHaveText(ACCOUNT_HEADER_TEXT);
        });
        it('should be able to open contact form after dismissing changes', () => {
            Account.editContactDataButton.click();
            expect(EditContactData.headerText).toHaveText(EDIT_CONTACT_DATA_HEADER_TEXT);
        });
        it('should be able see contact data was not updated', () => {
            EditContactData.verifyData(EDIT_NAME, EDIT_SURNAME, EDIT_PHONE, EDIT_EMAIL, EDIT_STREET, 
                EDIT_NUMBER, EDIT_POSTAL_CODE, EDIT_POSTAL_CITY);
        });
        it('should be able to go back', ()=> {
            driver.back();
            expect(Account.headerText).toHaveText(ACCOUNT_HEADER_TEXT);
        })
    });


    // NEWS
    describe('As a user I want to be able to get latest News', () => {
        beforeAll(() => {
            Account.newsButton.click();
            expect(News.headerText).toHaveText(NEWS_HEADER_TEXT);
        });
        it('should be able to open latest News', () => {
            News.latestNews.click();
            expect(News.whatIsNewHeaderImage).toBeDisplayed();
        });
        it('should be able to scroll latest news with next button', () => {
            while (News.nextButton.isDisplayed()) {
                News.nextButton.click();
            }
            expect(News.headerText).toHaveText(NEWS_HEADER_TEXT);
        });
        it('should be able to scroll latest news with back button', () => {
            News.latestNews.click();
            News.nextButton.click();
            while (News.nextButton.getText().match('NEXT')) {
                News.nextButton.click();
            }
            while (News.skipOrGoBackButton.getText().match('BACK')) {
                News.skipOrGoBackButton.click();
            }
            News.skipOrGoBackButton.click();
            expect(News.headerText).toHaveText(NEWS_HEADER_TEXT);
        });
        it('should be able to skip latest news with skip button', () => {
            News.latestNews.click();
            if (News.skipOrGoBackButton.getText().match('SKIP')) {
                News.skipOrGoBackButton.click();
            }
            expect(News.headerText).toHaveText(NEWS_HEADER_TEXT); 
        });
        it('should be able to go back to account', () => {
            News.backButton.click();
            expect(Account.headerText).toHaveText(ACCOUNT_HEADER_TEXT); 
        });
    });

    //SUPPORT
    describe('As a user I want to be able to get in touch with support', () => {
        it('should be able to open support', () => {
            Account.supportButton.click();
            expect(Account.supportMessage).toHaveTextContaining('Dear luca support,');
        });
        it('should be able to go back to the app ', () => {
            driver.back();
            expect(Account.headerText).toHaveText(ACCOUNT_HEADER_TEXT);
        });
    });

    //DOWNLOAD DATA
    describe('As a user I want to be able to download data', () => {

    describe('As a user I want to be able to request data', () => {
        it('should be able to open dropdown request data', () => {
            Account.requestDataButton.click();
            expect(Account.requestDataDropdown).toBeDisplayed();
        });
        it('should be able to request Contact tracing', () => {
            AndroidSettings.findAndroidElementByText('Contact tracing').click();
            expect(Account.saveButton).toBeDisplayed();
        });
        it('should be able to rename Contact tracing file', () => {
            addTimestampToName(CONTACT_TRACING_FILENAME);
        });
        it('should be able to save Contact tracing', () => {
            Account.saveButton.click();
            expect(Account.headerText).toHaveText(ACCOUNT_HEADER_TEXT);
        });
        it('should be able to open dropdown request data', () => {
            Account.requestDataButton.click();
            expect(Account.requestDataDropdown).toBeDisplayed();
        });
        it('should be able to request COVID certificates', () => {
            AndroidSettings.findAndroidElementByText('COVID certificate').click();
            expect(Account.saveButton).toBeDisplayed();
        });
        it('should be able to rename COVID certificates file', () => {
            addTimestampToName(COVID_CERTIFICATE_FILENAME);
        });
        it('should be able to save COVID certificates', () => {
            Account.saveButton.click();
            expect(Account.headerText).toHaveText(ACCOUNT_HEADER_TEXT);
        });
    });

    describe('As a user I want to be able to get information about daily key', () => {
        it('should be able to access daily key information', () => {
            Gestures.swipeUp();
            Account.dailyKeyButton.click();
            expect(Account.dailyKeyDateText).toBeDisplayed();
            expect(Account.dailyKeyIssuerText).toBeDisplayed();
            expect(Account.dailyKeySignedImage).toBeDisplayed();
        });
        it('should be able to download certificate', () => {
            Account.dailyKeydownloadCertificateButton.click();
            expect(Account.saveButton).toBeDisplayed();
        });
        it('should be able to rename Daily key file', () => {
            addTimestampToName(DAILY_KEY_FILENAME);
        });
        it('should be able to save the file', () => {
            Account.saveButton.click();
            expect(Account.dailyKeydownloadCertificateButton).toBeDisplayed();
        });
        it('should be able to go back', () => {
            Account.dailyKeyBackButton.click();
            expect(Account.headerText).toHaveText(ACCOUNT_HEADER_TEXT);
        });
    });
    describe('As a user I want to be able to get information about daily key', () => {
        it('should be able to see saved documents', () => { 
            FilesScreen.verifyFilesCreated();
            expect(Account.headerText).toHaveText(ACCOUNT_HEADER_TEXT);
        });
    });
});
    //REDIRECTIONS
    describe('As a user I want to be able to get redirected to the browser', () => {
        afterEach(() => {
            expect(Account.headerText).toHaveText(ACCOUNT_HEADER_TEXT);
        });
    
        it('should be able to see FAQ', () => {
            Account.faqButton.click();
            let url: string = Account.urlBar.getText();
            expect(url).toEqual(FAQ);
            driver.back();
        });
    
        it('should be able to see Terms of use', () => {
            Account.termsAndConditionsButton.click();
            let url: string = Account.urlBar.getText();
            expect(url).toEqual(TERMS_AND_CONDITIONS);
            driver.back();
        });
        it('should be able to see Privacy Policy', () => {
            Account.dataProtectionButton.click();
            let url: string = Account.urlBar.getText();
            expect(url).toEqual(PRIVACY_POLICY);
            driver.back();
        });
        it('should be able to see Imprint', () => {
            Account.imprintButton.click();
            driver.pause(3000);
            expect(Account.urlBar).toHaveText(IMPRINT_SLOW);
            driver.back();
        });
        it('should be able to see GitLab', () => {
            Gestures.swipeUp();
            Account.sourceCodeButton.click();
            Account.openGitlabButton.click();
            let url: string = Account.urlBar.getText();
            expect(url).toEqual(GITLAB);
            driver.back();
        });
    });

    //SEE APP DATA
    describe('As a user I want to be able to see app data', () => {
        beforeAll(() => {
            Gestures.swipeUp();
            expect(Account.appDataButton).toBeDisplayed();
        });
        it('should be able to open app data', () => {
            Account.appDataButton.click();
            expect(Account.entityHeaderTitle).toHaveTextContaining('luca');
        });
        it('should be able to go back to the app ', () => {
            driver.back();
            expect(Account.headerText).toHaveText(ACCOUNT_HEADER_TEXT);
        });
    });

    //CHECK APP VERSION
    describe('As a user I want to be able to see app version', () => {
        beforeAll(() => {
            Gestures.swipeUp();
            expect(Account.appDataButton).toBeDisplayed();
        });
        it('should be able to open app version', () => {
            Account.appVersionButton.click();
            expect(Account.alert).toHaveText('App version');
        });
        it('should be able to close app version', () => {
            Account.okButton.click();
            expect(Account.alert).not.toBeDisplayed();
        });
});
