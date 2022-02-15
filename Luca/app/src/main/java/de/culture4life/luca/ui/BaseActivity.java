package de.culture4life.luca.ui;

import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.appcompat.app.AppCompatActivity;

import de.culture4life.luca.LucaApplication;
import de.culture4life.luca.Manager;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public abstract class BaseActivity extends AppCompatActivity {

    protected LucaApplication application;

    protected final CompositeDisposable activityDisposable = new CompositeDisposable();

    @CallSuper
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        application = (LucaApplication) getApplication();
    }

    @Override
    protected void onStart() {
        super.onStart();
        application.onActivityStarted(this);
    }

    @Override
    protected void onStop() {
        application.onActivityStopped(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        activityDisposable.dispose();
        super.onDestroy();
    }

    public <ManagerType extends Manager> Single<ManagerType> getInitializedManager(ManagerType manager) {
        return application.getInitializedManager(manager);
    }

    public void showActionBar() {
        getSupportActionBar().show();
    }

    public void hideActionBar() {
        getSupportActionBar().hide();
    }

}
