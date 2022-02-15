import { searchElementBuilder } from '../helpers/Utils';

class AddChild {
    get mainText ():WebdriverIO.Element {return $(searchElementBuilder('childAddingDescriptionTextView'));};
    get addChildButton ():WebdriverIO.Element {return $(searchElementBuilder('primaryActionButton'));};
    get childFirstName ():WebdriverIO.Element {return $(searchElementBuilder('chilFirstNameText'));};
    get childLastName ():WebdriverIO.Element {return $(searchElementBuilder('childLastNameText'));};
    get alertButtonOk():WebdriverIO.Element {return $('[id="android:id/button1"]');};
    get alertText():WebdriverIO.Element {return $('[id="android:id/message"]');};
    get childNameTextView () :WebdriverIO.Element {return $(searchElementBuilder('childNameTextView'));};
    get addChildLayout():WebdriverIO.Element {return $(searchElementBuilder('addChildLayout'));};
    get removeChildImg():WebdriverIO.Element {return $(searchElementBuilder('removeChildImageView'));};
    
    addChild(firstName:string, lastName:string){
        this.addChildButton.click();
        this.childFirstName.setValue(firstName);
        this.childLastName.setValue(lastName);
        this.addChildButton.click()
    }
}
export default new AddChild();