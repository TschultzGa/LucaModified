package de.culture4life.luca.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannedString;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.FragmentNavigator;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.tbruyelle.rxpermissions3.Permission;
import com.tbruyelle.rxpermissions3.RxPermissions;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import de.culture4life.luca.LucaApplication;
import de.culture4life.luca.R;
import de.culture4life.luca.ui.dialog.BaseDialogFragment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import timber.log.Timber;

public abstract class BaseFragment<ViewModelType extends BaseViewModel> extends Fragment {

    protected final PublishSubject<ActivityResult> activityResults = PublishSubject.create();
    protected ActivityResultLauncher<Intent> getActivityResult;

    protected LucaApplication application;

    protected ViewModelType viewModel;

    protected CompositeDisposable viewDisposable;

    protected RxPermissions rxPermissions;

    protected NavController navigationController;

    @Nullable
    protected Snackbar errorSnackbar;

    protected boolean initialized;

    @Nullable
    protected ImageView backImageView;

    @NonNull
    @CallSuper
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewDisposable = new CompositeDisposable();
        ViewBinding viewBinding = getViewBinding();
        if (viewBinding != null) {
            return viewBinding.getRoot();
        } else if (getLayoutResource() != -1) {
            return inflater.inflate(getLayoutResource(), container, false);
        } else {
            throw new IllegalStateException("Fragment needs to override either getViewBinding() or getLayoutResource()");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        application = (LucaApplication) getActivity().getApplication();
        rxPermissions = new RxPermissions(this);
        try {
            navigationController = Navigation.findNavController(view);
        } catch (Exception e) {
            Timber.w("No navigation controller available");
        }

        initializeViewModel()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete(() -> {
                    initializeViews();
                    this.initialized = true;
                })
                .subscribe(
                        () -> Timber.d("Initialized %s with %s", this, viewModel),
                        throwable -> Timber.e(throwable, "Unable to initialize %s with %s: %s", this, viewModel, throwable.toString())
                );
    }

    @Override
    public void onAttach(@NonNull @NotNull Context context) {
        super.onAttach(context);
        getActivityResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::emitActivityResult);
    }

    private void emitActivityResult(ActivityResult activityResult) {
        viewDisposable.add(Completable.fromAction(() -> activityResults.onNext(activityResult))
                .subscribeOn(Schedulers.io())
                .subscribe());
    }

    @CallSuper
    @Override
    public void onStart() {
        super.onStart();
        viewDisposable = new CompositeDisposable();
        viewDisposable.add(waitUntilInitializationCompleted()
                .andThen(viewModel.processArguments(getArguments()))
                .andThen(viewModel.keepDataUpdated())
                .doOnSubscribe(disposable -> Timber.d("Keeping data updated for %s", this))
                .doOnError(throwable -> Timber.w(throwable, "Unable to keep data updated for %s", this))
                .retryWhen(errors -> errors.delay(1, TimeUnit.SECONDS))
                .doFinally(() -> Timber.d("Stopping to keep data updated for %s", this))
                .subscribe());
    }

    @CallSuper
    @Override
    public void onStop() {
        viewDisposable.dispose();
        if (errorSnackbar != null && errorSnackbar.isShown()) {
            errorSnackbar.dismiss();
        }
        super.onStop();
    }

    /**
     * Overwrite this to return a ViewBinding instead of a layout resource.
     *
     * @return ViewBinding instance
     */
    @Nullable
    protected ViewBinding getViewBinding() {
        return null;
    }

    @LayoutRes
    protected int getLayoutResource() {
        return -1;
    }

    protected abstract Class<ViewModelType> getViewModelClass();

    private Completable waitUntilInitializationCompleted() {
        return Observable.interval(0, 50, TimeUnit.MILLISECONDS)
                .filter(tick -> initialized)
                .firstOrError()
                .ignoreElement();
    }

    @CallSuper
    protected Completable initializeViewModel() {
        return Single.fromCallable(() -> new ViewModelProvider(getActivity()).get(getViewModelClass()))
                .doOnSuccess(createdViewModel -> {
                    viewModel = createdViewModel;
                    viewModel.setNavigationController(navigationController);
                })
                .observeOn(Schedulers.io())
                .flatMapCompletable(BaseViewModel::initialize);
    }

    @CallSuper
    protected void initializeViews() {
        setupBackButton();
        observeErrors();
        observeRequiredPermissions();
    }

    protected void setupBackButton() {
        backImageView = getView().findViewById(R.id.actionBarBackButtonImageView);
        if (backImageView == null) {
            return;
        }
        backImageView.setOnClickListener(view -> navigationController.popBackStack());
    }

    protected boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            default: {
                Timber.w("Unknown menu item selected: %s", item.getTitle());
                return false;
            }
        }
    }

    protected <ValueType> void observe(@NonNull LiveData<ValueType> liveData, @NonNull Observer<ValueType> observer) {
        liveData.observe(getViewLifecycleOwner(), observer);
    }

    protected void observeRequiredPermissions() {
        observe(viewModel.getRequiredPermissionsViewEvent(), permissionsViewEvent -> {
            Set<String> permissions = permissionsViewEvent.getValue();
            if (permissionsViewEvent.hasBeenHandled() || permissions.isEmpty()) {
                return;
            }
            permissionsViewEvent.setHandled(true);

            String[] keys = new String[permissions.size()];
            permissions.toArray(keys);

            rxPermissions.requestEach(keys)
                    .doOnSubscribe(disposable -> Timber.d("Requesting required permissions: %s", permissions))
                    .doOnError(throwable -> Timber.e(throwable, "Unable to request permissions: %s", permissions))
                    .onErrorComplete()
                    .subscribe(this::onPermissionResult);
        });
    }

    protected void onPermissionResult(Permission permission) {
        Timber.v("Permission result: %s", permission);
        viewModel.onPermissionResult(permission);
    }

    protected void observeErrors() {
        observe(viewModel.getErrors(), errors -> {
            if (errors.isEmpty()) {
                indicateNoErrors();
            } else {
                indicateErrors(errors);
            }
        });
    }

    protected void indicateNoErrors() {
        if (errorSnackbar != null) {
            errorSnackbar.dismiss();
        }
    }

    protected void indicateErrors(@NonNull Set<ViewError> errors) {
        Timber.d("indicateErrors() called on %s with: errors = [%s]", this, errors);
        for (ViewError error : errors) {
            showErrorAsDialog(error);
        }
    }

    protected void showErrorAsToast(@NonNull ViewError error) {
        if (getContext() == null) {
            return;
        }
        Toast.makeText(getContext(), error.getTitle(), Toast.LENGTH_LONG).show();
        viewModel.onErrorShown(error);
        viewModel.onErrorDismissed(error);
    }

    protected void showErrorAsSnackbar(@NonNull ViewError error) {
        if (getView() == null) {
            return;
        }
        if (errorSnackbar != null) {
            errorSnackbar.dismiss();
        }
        int duration = error.isResolvable() ? Snackbar.LENGTH_INDEFINITE : Snackbar.LENGTH_LONG;
        errorSnackbar = Snackbar.make(getView(), error.getTitle(), duration);
        errorSnackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onShown(Snackbar snackbar) {
                viewModel.onErrorShown(error);
            }

            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                viewModel.onErrorDismissed(error);
            }
        });

        if (error.isResolvable()) {
            errorSnackbar.setAction(error.getResolveLabel(), action -> viewDisposable.add(error.getResolveAction()
                    .subscribe(
                            () -> Timber.d("Error resolved"),
                            throwable -> Timber.w("Unable to resolve error: %s", throwable.toString())
                    )));
        }
        errorSnackbar.show();
    }

    protected void showErrorAsDialog(@NonNull ViewError error) {
        if (getView() == null) {
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity())
                .setTitle(error.getTitle())
                .setMessage(error.getDescription());

        if (error.isResolvable()) {
            builder.setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.cancel())
                    .setPositiveButton(error.getResolveLabel(), (dialog, which) -> viewDisposable.add(error.getResolveAction()
                            .subscribe(
                                    () -> Timber.d("Error resolved"),
                                    throwable -> Timber.w("Unable to resolve error: %s", throwable.toString())
                            )));
        } else {
            builder.setPositiveButton(R.string.action_ok, (dialog, which) -> {
                // do nothing
            });
        }

        BaseDialogFragment dialogFragment = new BaseDialogFragment(builder);
        dialogFragment.setOnDismissListener(dialog -> viewModel.onErrorDismissed(error));
        dialogFragment.show();

        viewModel.onErrorShown(error);
    }

    protected void hideKeyboard() {
        View view = getView();
        if (view == null) {
            Timber.w("Unable to hide keyboard, view not available");
            return;
        }

        // Get compat inset controller which backports the inset api to before level 30
        WindowInsetsControllerCompat windowInsetsController = ViewCompat.getWindowInsetsController(view);
        if (windowInsetsController == null) {
            Timber.w("Unable to hide keyboard, insets controller not available");
            return;
        }

        // Use compat inset controller to hide keyboard (IME). Counterpart would be windowInsetsController.show(...);
        windowInsetsController.hide(WindowInsetsCompat.Type.ime());
    }

    protected void safeNavigateFromNavController(@IdRes int destination) {
        safeNavigateFromNavController(destination, null);
    }

    protected void safeNavigateFromNavController(@IdRes int destination, @Nullable Bundle bundle) {
        FragmentNavigator.Destination currentDestination = (FragmentNavigator.Destination) navigationController.getCurrentDestination();
        boolean isCurrentDestination = getClass().getName().equals(currentDestination.getClassName());
        if (isCurrentDestination) {
            navigationController.navigate(destination, bundle);
        }
    }

    protected Single<Uri> getFileExportUri(@NonNull String fileName) {
        return Observable.defer(() -> {
            Intent createFileIntent = new ActivityResultContracts.CreateDocument()
                    .createIntent(getContext(), fileName);
            createFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
            createFileIntent.setType("text/plain");
            getActivityResult.launch(createFileIntent);
            return activityResults;
        }).firstOrError()
                .filter(activityResult -> activityResult.getResultCode() == Activity.RESULT_OK)
                .map(activityResult -> activityResult.getData().getData())
                .switchIfEmpty(Single.error(new UserCancelledException()));
    }

    protected Single<Uri> getFileImportUri(@NonNull String[] mimeTypes) {
        return Observable.defer(() -> {
            Intent createFileIntent = new ActivityResultContracts.OpenDocument()
                    .createIntent(getContext(), mimeTypes);
            getActivityResult.launch(createFileIntent);
            return activityResults;
        }).firstOrError()
                .filter(activityResult -> activityResult.getResultCode() == Activity.RESULT_OK)
                .map(activityResult -> activityResult.getData().getData())
                .switchIfEmpty(Single.error(new UserCancelledException()));
    }

    public CharSequence getFormattedString(@StringRes int id, Object... args) {
        for (int i = 0; i < args.length; ++i) {
            args[i] = args[i] instanceof String ? TextUtils.htmlEncode((String) args[i]) : args[i];
        }
        String html = HtmlCompat.toHtml(new SpannedString(getText(id)), HtmlCompat.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL);
        return HtmlCompat.fromHtml(String.format(html, args), HtmlCompat.FROM_HTML_MODE_COMPACT);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

}