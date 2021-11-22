import { searchElementBuilder } from '../../helpers/Utils';
class ContactDataInfo {
    get descriptionTextView ():WebdriverIO.Element {return $(searchElementBuilder('descriptionTextView'));}
    get primaryActionButton ():WebdriverIO.Element {return $(searchElementBuilder('primaryActionButton'));}
}

export default new ContactDataInfo();
