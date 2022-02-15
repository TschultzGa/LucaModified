package de.culture4life.luca;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

import de.culture4life.luca.util.TimeUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Base class for managers that require initialization. Allows multiple concurrent subscriptions to
 * {@link #initialize(Context)} but will make sure that {@link #doInitialize(Context)} is only done
 * once.
 */
public abstract class Manager {

    private boolean isInitializing;
    private boolean isInitialized;

    protected Context context;

    protected CompositeDisposable managerDisposable;

    @CallSuper
    public Completable initialize(@NonNull Context context) {
        return Completable.defer(() -> {
            synchronized (this) {
                if (isInitializing && !isInitialized) {
                    wait();
                }
                isInitializing = !isInitialized;
            }
            if (!isInitialized) {
                long startTime = TimeUtil.getCurrentMillis();
                this.context = context.getApplicationContext();
                this.managerDisposable = new CompositeDisposable();
                return doInitialize(context)
                        .doOnComplete(() -> {
                            isInitialized = true;
                            Timber.i("Completed initialization of %s in %d ms", this, (TimeUtil.getCurrentMillis() - startTime));
                        })
                        .doFinally(() -> {
                            isInitializing = false;
                            synchronized (this) {
                                notifyAll();
                            }
                        });
            } else {
                synchronized (this) {
                    notifyAll();
                }
            }
            return Completable.complete();
        });
    }

    protected abstract Completable doInitialize(@NonNull Context context);

    @CallSuper
    public void dispose() {
        if (isInitialized) {
            isInitialized = false;
            managerDisposable.dispose();
        }
    }

    protected <Type> Single<Type> getInitializedField(Type field) {
        return Single.defer(() -> {
            if (field != null) {
                return Single.just(field);
            } else {
                return Single.error(new IllegalStateException(this.getClass().getSimpleName() + " has not been initialized yet"));
            }
        });
    }

    public boolean isInitializing() {
        return isInitializing;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public Completable invoke(Completable completable) {
        return invokeDelayed(completable, 0);
    }

    public Completable invokeDelayed(Completable completable, long delay) {
        return Completable.fromAction(() -> managerDisposable.add(completable
                .doOnError(throwable -> Timber.w("Invoked completable emitted an error: %s", throwable.toString()))
                .onErrorComplete()
                .delaySubscription(delay, TimeUnit.MILLISECONDS, Schedulers.io())
                .subscribe()));
    }

    public LucaApplication getApplication() {
        return (LucaApplication) context;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

}
