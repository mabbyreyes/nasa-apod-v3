package edu.cnm.deepdive.nasaapod.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.Lifecycle.Event;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.OnLifecycleEvent;
import edu.cnm.deepdive.nasaapod.model.entity.Apod;
import edu.cnm.deepdive.nasaapod.model.pojo.ApodWithStats;
import edu.cnm.deepdive.nasaapod.model.repository.ApodRepository;
import edu.cnm.deepdive.nasaapod.service.ApodService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Implements indicates its a lifetime observer.
public class MainViewModel extends AndroidViewModel implements LifecycleObserver {

  private final MutableLiveData<Apod> apod;
  private final MutableLiveData<Throwable> throwable;
  private final MutableLiveData<Set<String>> permissions;
  private final CompositeDisposable pending;
  private final ApodRepository repository;

  public MainViewModel(@NonNull Application application) {
    super(application);
    repository = ApodRepository.getInstance();
    apod = new MutableLiveData<>();
    throwable = new MutableLiveData<>();
    // Knows that set of string goes in angled brackets, and hashset is string.
    permissions = new MutableLiveData<>(new HashSet<>());
    pending = new CompositeDisposable();
    Date today = new Date();
    String formattedDate = ApodService.DATE_FORMATTER.format(today);
    try {
      setApodDate(ApodService.DATE_FORMATTER
          .parse(formattedDate)); // TODO Investigate adjustment for NASA APOD-relevant time zone.
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  public LiveData<List<ApodWithStats>> getAllApodSummaries() {
    return repository.get();
  }

  public LiveData<Apod> getApod() {
    return apod;
  }

  public LiveData<Throwable> getThrowable() {
    return throwable;
  }

  // Never get for MutableLiveData, always LiveData.
  public LiveData<Set<String>> getPermissions() {
    return permissions;
  }

  //  Returns true if value in set.
  public void grantPermission(String permission) {
    Set<String> permissions = this.permissions.getValue();
    if (permissions.add(permission)) {
      this.permissions.setValue(permissions);
    }
  }

  // False if it wasn't there in the first place, nothings changed.
  public void revokePermission(String permission) {
    Set<String> permissions = this.permissions.getValue();
    if (permissions.remove(permission)) {
      this.permissions.setValue(permissions);
    }
  }

  // Puts in live data, if fails, puts in throwable.
  public void setApodDate(Date date) {
    throwable.setValue(null);
    pending.add(
        // The "get" gets apod image for date.
        repository.get(date)
            // Returns disposable object.
            .subscribe(
                apod::postValue,
                throwable::postValue
            )
    );
  }

  // Invokes consumer with new image.
  public void getImage(@NonNull Apod apod, @NonNull Consumer<String> pathConsumer) {
    // When starting new task, clear bucket so no error messages left behind.
    throwable.setValue(null);
    // Asks repository for image. If success, executes consumer, then run.
    pending.add(
        repository.getImage(apod)
            // UI thread.
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                // Runs on UI thread.
                pathConsumer,
                // Unsuccessful then throws.
                throwable::setValue
            )
    );
  }

  public void downloadImage(@NonNull Apod apod, Action onSuccess) {
    throwable.setValue(null);
    pending.add(
        repository.downloadImage(apod)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            onSuccess,
            throwable::setValue
        )
    );
  }

  // If app gets stopped, this gets executed. It empties the bucket of pending tasks.
  @SuppressWarnings("unused")
  @OnLifecycleEvent(Event.ON_STOP)
  private void disposePending() {
    pending.clear();
  }

}
