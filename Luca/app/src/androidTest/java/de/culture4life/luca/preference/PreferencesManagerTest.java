package de.culture4life.luca.preference;

import org.junit.Before;
import org.junit.Test;

import de.culture4life.luca.LucaInstrumentationTest;
import de.culture4life.luca.crypto.CryptoManager;

public class PreferencesManagerTest extends LucaInstrumentationTest {

    private static final String TEST_KEY = "testKey";
    private static final String TEST_VALUE = "testString";

    private PreferencesManager preferencesManager;
    private CryptoManager cryptoManager;

    @Before
    public void setup() {
        cryptoManager = getInitializedManager(application.getCryptoManager());
        preferencesManager = getInitializedManager(application.getPreferencesManager());
    }

    @Test
    public void restore_persistedValue_isSame() {
        preferencesManager.persist(TEST_KEY, TEST_VALUE)
                .andThen(preferencesManager.restore(TEST_KEY, String.class))
                .test()
                .assertValue(TEST_VALUE);
    }

    @Test
    public void containsKey_afterDeletingAccountAndInitialize_false() {
        preferencesManager.persist(TEST_KEY, TEST_VALUE)
                .andThen(cryptoManager.deleteAllKeyStoreEntries())
                .andThen(preferencesManager.doInitialize(application))
                .andThen(preferencesManager.containsKey(TEST_KEY))
                .test()
                .assertValue(false);
    }

}