import MyLucaScreen from '../screenObjects/MyLuca.Screen';
import AddChild from '../screenObjects/AddChild.Screen';
import Menu from '../screenObjects/Menu.Component';
import { register, sauceLabsImageInjection, deleteUser } from '../helpers/Utils';
import { 
    MY_LUCA_DESCRIPTION_TEXT, MY_LUCA_DESCRIPTION_TITLE_TEXT, MY_LUCA_SCREEN_TITLE,
    MY_LUCA_ADD_DOCUMENT_MESSAGE, VALID_QR_VACCINE_CERTIFICATE_1TH_DOSE, VALID_QR_VACCINE_CERTIFICATE_2TH_DOSE,
    VALID_COVID_CERTIFICATE_1TH_DOSE_DEEPLINK, VALID_COVID_CERTIFICATE_2TH_DOSE_DEEPLINK, COVID_CERTIFICATE_DIFFERENT_NAME_DEEPLINK,
    QR_COVID_CERTIFICATE_DIFFERENT_NAME, MY_LUCA_ADD_DOCUMENT_DIFFERENT_NAME_MESSAGE, VALID_NAME, VALID_SURNAME,
    ADD_CHILD_MAIN_TEXT_AFTER_ADD, ADD_CHILD_MAIN_TEXT, DELETE_CHILD_MESSAGE, EMPTY_IMAGE, MY_LUCA_DELETE_DOCUMENT_MESSAGE
} from '../helpers/Constants';

describe('As a user I want to to be able do access MyLuca Functionality’s ', () => {
    beforeAll(()=>{
        register()
    })
    beforeEach(()=>{
        Menu.myLucaIcon.click();
    })
    describe('As a user I want to to be able to add and delete Covid Certificate', () => {
        it('should load all elements', () => {
            expect(MyLucaScreen.myLucaDescriptionText).toHaveText(MY_LUCA_DESCRIPTION_TEXT);
            expect(MyLucaScreen.myLucaDescriptionTitleText).toHaveText(MY_LUCA_DESCRIPTION_TITLE_TEXT);
            expect(MyLucaScreen.myLucaTitleText).toHaveText(MY_LUCA_SCREEN_TITLE);
            expect(MyLucaScreen.addChildButton).toBeDisplayed();
            expect(MyLucaScreen.bookAppointmentButton).toBeDisplayed();
            expect(MyLucaScreen.addDocumentButton).toBeDisplayed();
        });
        it('should be able add covid vaccine first shot', () => {
            MyLucaScreen.addDocument(VALID_QR_VACCINE_CERTIFICATE_1TH_DOSE, VALID_COVID_CERTIFICATE_1TH_DOSE_DEEPLINK)         
            expect(MyLucaScreen.alertText).toHaveText(MY_LUCA_ADD_DOCUMENT_MESSAGE);
            MyLucaScreen.alertButtonOk.click();
            expect(MyLucaScreen.documentCard).toBeDisplayed();
            expect(MyLucaScreen.firstCovidCertificateText).toBeDisplayed();
            sauceLabsImageInjection(EMPTY_IMAGE)
        });
        it('should be able add covid vaccine second shot', () => {
            MyLucaScreen.addDocument(VALID_QR_VACCINE_CERTIFICATE_2TH_DOSE, VALID_COVID_CERTIFICATE_2TH_DOSE_DEEPLINK)
            expect(MyLucaScreen.alertText).toHaveText(MY_LUCA_ADD_DOCUMENT_MESSAGE);
            MyLucaScreen.alertButtonOk.click();
            expect(MyLucaScreen.pageIndicator).toBeDisplayed();
            expect(MyLucaScreen.CompleteCovidCertificateText).toBeDisplayed();
            sauceLabsImageInjection(EMPTY_IMAGE)
        });
        it('should display message in case certificate name is different then user name', () => {
            MyLucaScreen.addDocument(QR_COVID_CERTIFICATE_DIFFERENT_NAME, COVID_CERTIFICATE_DIFFERENT_NAME_DEEPLINK)
            expect(MyLucaScreen.alertText).toHaveText(MY_LUCA_ADD_DOCUMENT_DIFFERENT_NAME_MESSAGE);
            MyLucaScreen.alertButtonOk.click();
        });
        it('should be able to delete covid vaccine second shot', () => {
            MyLucaScreen.deleteDocument();
            expect(MyLucaScreen.alertText).toHaveText(MY_LUCA_DELETE_DOCUMENT_MESSAGE);
            MyLucaScreen.alertButtonOk.click();
            expect(MyLucaScreen.pageIndicator.isExisting()).toBeFalse();
            expect(MyLucaScreen.firstCovidCertificateText).toBeDisplayed();
        });
        it('should be able to delete covid vaccine first shot', () => {
            MyLucaScreen.deleteDocument();
            MyLucaScreen.alertButtonOk.click();
            MyLucaScreen.myLucaDescriptionText.waitForExist();
            expect(MyLucaScreen.myLucaDescriptionText).toHaveText(MY_LUCA_DESCRIPTION_TEXT);
            expect(MyLucaScreen.myLucaDescriptionTitleText).toHaveText(MY_LUCA_DESCRIPTION_TITLE_TEXT);
            expect(MyLucaScreen.myLucaTitleText).toHaveText(MY_LUCA_SCREEN_TITLE);
        });
    });

    afterAll(()=>{
        driver.closeApp();
        driver.startActivity('de.culture4life.luca.'+process.env.BUILD,'de.culture4life.luca.ui.splash.SplashActivity','de.culture4life.luca.'+process.env.BUILD,'de.culture4life.luca.ui.MainActivity');
        deleteUser()
    })
});
