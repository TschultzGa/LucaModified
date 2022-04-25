package de.culture4life.luca.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;

import de.culture4life.luca.LucaUnitTest;

public class SerializationUtilTest extends LucaUnitTest {

    private final String TEST_STRING = "abcde1234!§$% äöü @ ,.-;:_*<>";

    @Test
    public void serializeToAndFromBase32() {
        SerializationUtil.toBase32(TEST_STRING.getBytes())
                .map(SerializationUtil::fromBase32)
                .test()
                .assertValue(value -> Arrays.equals(value.blockingGet(), TEST_STRING.getBytes()));
    }

    @Test
    public void serializeToAndFromBase64() {
        SerializationUtil.toBase64(TEST_STRING.getBytes())
                .map(SerializationUtil::fromBase64)
                .test()
                .assertValue(value -> Arrays.equals(value.blockingGet(), TEST_STRING.getBytes()));
    }

    @Test
    public void serializeToAndFromJson() {
        HashMap<String, String> testObject = new HashMap<>();
        testObject.put("testString", TEST_STRING);
        SerializationUtil.toJson(testObject)
                .map(serialized -> SerializationUtil.fromJson(serialized, HashMap.class))
                .test()
                .assertValue(value -> value.blockingGet().equals(testObject));
    }

}
