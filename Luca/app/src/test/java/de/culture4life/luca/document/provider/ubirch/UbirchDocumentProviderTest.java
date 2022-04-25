package de.culture4life.luca.document.provider.ubirch;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import de.culture4life.luca.LucaUnitTest;
import de.culture4life.luca.document.DocumentParsingException;
import de.culture4life.luca.document.DocumentVerificationException;
import de.culture4life.luca.registration.Person;

public class UbirchDocumentProviderTest extends LucaUnitTest {

    private static final String VALID_TEST_RESULT = "https://verify.govdigital.de/v/gd/#f=Tester;g=Tom;b=19671215;d=202104132336;r=n;t=PCR;s=TKH6M4I9pM8kQ5i1;i=12345;p=demodemo";
    private static final String UNVERIFIED_TEST_RESULT = "https://verify.govdigital.de/v/gd/#f=Mustermann;g=John;b=19640812;p=T01000322;i=3CF75K8D0L;d=202007011030;t=PCR;r=n;s=2fe00c151cb726bb9ed7";
    private static final String UNSUPPORTED_TEST_RESULT = "https://invalid.provider.de/v/gd/#f=Tester;g=Tom;b=19671215;d=202104132336;r=n;t=PCR;s=TKH6M4I9pM8kQ5i1;i=12345;p=demodemo";

    private UbirchDocumentProvider testResultProvider;

    @Before
    public void setUp() {
        testResultProvider = new UbirchDocumentProvider();
    }

    @Test
    public void canParse_validData_emitsTrue() {
        testResultProvider.canParse(VALID_TEST_RESULT)
                .test()
                .assertValue(true);
    }

    @Test
    public void canParse_invalidData_emitsFalse() {
        testResultProvider.canParse(UNSUPPORTED_TEST_RESULT)
                .test()
                .assertValue(false);
    }

    @Test
    public void parse_validData_parsesData() {
        testResultProvider.parse(VALID_TEST_RESULT)
                .test()
                .assertValue(testResult -> {
                    assertEquals("Tester", testResult.f);
                    assertEquals("Tom", testResult.g);
                    assertEquals("19671215", testResult.b);
                    assertEquals("demodemo", testResult.p);
                    assertEquals("12345", testResult.i);
                    assertEquals("202104132336", testResult.d);
                    assertEquals("PCR", testResult.t);
                    assertEquals("n", testResult.r);
                    assertEquals("TKH6M4I9pM8kQ5i1", testResult.s);
                    assertEquals("{\"b\":\"19671215\",\"d\":\"202104132336\",\"f\":\"Tester\",\"g\":\"Tom\",\"i\":\"12345\",\"p\":\"demodemo\",\"r\":\"n\",\"s\":\"TKH6M4I9pM8kQ5i1\",\"t\":\"PCR\"}", testResult.toCompactJson());
                    return true;
                });
    }

    @Test
    public void parse_invalidData_emitsError() {
        testResultProvider.parse(UNSUPPORTED_TEST_RESULT)
                .test()
                .assertError(DocumentParsingException.class);
    }

    @Test
    @Ignore(value = "unable to find valid certification path to requested target")
    public void validate_validData_completes() {
        Person person = new Person("Tom", "Tester");
        testResultProvider.parse(VALID_TEST_RESULT)
                .flatMapCompletable(testResult -> testResultProvider.validate(testResult, person))
                .test()
                .assertComplete();
    }

    @Test
    public void validate_nameMismatch_emitsError() {
        Person person = new Person("Erika", "Mustermann");
        testResultProvider.parse(VALID_TEST_RESULT)
                .flatMapCompletable(testResult -> testResultProvider.validate(testResult, person))
                .test()
                .assertError(DocumentVerificationException.class);
    }

    @Test
    public void validate_unverifiedData_emitsError() {
        Person person = new Person("John", "Mustermann");
        testResultProvider.parse(UNVERIFIED_TEST_RESULT)
                .flatMapCompletable(testResult -> testResultProvider.validate(testResult, person))
                .test()
                .assertError(DocumentVerificationException.class);
    }

}
