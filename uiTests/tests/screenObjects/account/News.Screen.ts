import { searchElementBuilder } from '../../helpers/Utils';


class News {
get headerText():WebdriverIO.Element {return $(searchElementBuilder('actionBarTitleTextView'));}
get backButton():WebdriverIO.Element {return $(searchElementBuilder('actionBarBackButtonImageView'));}
get latestNews():WebdriverIO.Element {return $(searchElementBuilder('pageGroup1'));}
get skipOrGoBackButton():WebdriverIO.Element {return $(searchElementBuilder('skipOrGoBackButton'));}
get nextButton():WebdriverIO.Element {return $(searchElementBuilder('nextButton'));}
get whatIsNewHeaderImage():WebdriverIO.Element {return $(searchElementBuilder('whatIsNewHeaderImage'));}
get descriptionTextView():WebdriverIO.Element {return $(searchElementBuilder('descriptionTextView'));}
get whatIsNewPagesIndicator():WebdriverIO.Element {return $(searchElementBuilder('whatIsNewPagesIndicator'));}


}

export default new News();