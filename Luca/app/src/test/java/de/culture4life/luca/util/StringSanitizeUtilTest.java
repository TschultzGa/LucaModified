package de.culture4life.luca.util;

import org.junit.Assert;
import org.junit.Test;

import de.culture4life.luca.LucaUnitTest;

public class StringSanitizeUtilTest extends LucaUnitTest {

    @Test
    public void sanitize_normalCharacters_unchanged() {
        Assert.assertEquals("Max Mustermann", StringSanitizeUtil.sanitize("Max Mustermann"));
        Assert.assertEquals("a@b.de", StringSanitizeUtil.sanitize("a@b.de"));
    }

    @Test
    public void sanitize_specialCharacters_replacedBySpace() {
        Assert.assertEquals("Max Mustermann     ", StringSanitizeUtil.sanitize("Max Mustermann '()\""));
    }

}
