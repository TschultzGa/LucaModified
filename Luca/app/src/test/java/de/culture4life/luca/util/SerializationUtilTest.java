package de.culture4life.luca.util;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashMap;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class SerializationUtilTest {

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
        HashMap testObject = new HashMap();
        testObject.put("testString", TEST_STRING);
        SerializationUtil.toJson(testObject)
                .map(serialized -> SerializationUtil.fromJson(serialized, HashMap.class))
                .test()
                .assertValue(value -> value.blockingGet().equals(testObject));
    }

}