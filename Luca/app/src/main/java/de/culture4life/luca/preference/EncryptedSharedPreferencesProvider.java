package de.culture4life.luca.preference;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;

import io.reactivex.rxjava3.core.Single;

/**
 * Provider that uses Androids {@link EncryptedSharedPreferences} for storing keys and values encrypted.
 */
public class EncryptedSharedPreferencesProvider extends com.nexenio.rxpreferences.provider.EncryptedSharedPreferencesProvider {

    public EncryptedSharedPreferencesProvider(@NonNull Context context) {
        super(context);
    }

    @Override
    protected Single<SharedPreferences> createEncryptedSharedPreferences(@NonNull Context context) {
        return super.createEncryptedSharedPreferences(context)
                .retry(1, throwable -> deletePreferencesFiles(context)
                        .andThen(Single.just(true))
                        .onErrorReturnItem(false)
                        .blockingGet());
    }

}
