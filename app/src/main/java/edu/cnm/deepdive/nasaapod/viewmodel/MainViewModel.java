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
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

// Implements indicates its a lifetime observer.
public class MainViewModel extends AndroidViewModel implements LifecycleObserver {

  private final MutableLiveData<Apod> apod;
  private final MutableLiveData<Throwable> throwable;
  private final CompositeDisposable pending;
  private final ApodRepository repository;

  public MainViewModel(@NonNull Application application) {
    super(application);
    ApodRepository.setContext(application);
    repository = ApodRepository.getInstance();
    apod = new MutableLiveData<>();
    throwable = new MutableLiveData<>();
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

  // If app gets stopped, this gets executed. It empties the bucket of pending tasks.
  @SuppressWarnings("unused")
  @OnLifecycleEvent(Event.ON_STOP)
  private void disposePending() {
    pending.clear();
  }

}
