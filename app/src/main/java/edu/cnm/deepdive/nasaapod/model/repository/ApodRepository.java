package edu.cnm.deepdive.nasaapod.model.repository;

import android.app.Application;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import edu.cnm.deepdive.nasaapod.BuildConfig;
import edu.cnm.deepdive.nasaapod.model.dao.AccessDao;
import edu.cnm.deepdive.nasaapod.model.dao.ApodDao;
import edu.cnm.deepdive.nasaapod.model.entity.Access;
import edu.cnm.deepdive.nasaapod.model.entity.Apod;
import edu.cnm.deepdive.nasaapod.model.entity.Apod.MediaType;
import edu.cnm.deepdive.nasaapod.model.pojo.ApodWithStats;
import edu.cnm.deepdive.nasaapod.service.ApodDatabase;
import edu.cnm.deepdive.nasaapod.service.ApodService;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.schedulers.Schedulers;
import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApodRepository {

  private static final int NETWORK_THREAD_COUNT = 10;
  // Regex string to pull file name and give it name with date.
  private static final Pattern URL_FILENAME_PATTERN =
      Pattern.compile("^.*/([^/#?]+)(?:\\?.*)?(?:#.*)?$");
  // % placeholder for first parameter, t is date or time, Y is 4 digit year, m is two digit month, d is two digit,
  private static final String LOCAL_FILENAME_FORMAT = "%1$tY%1$tm%1$td-%2$s";

  private final ApodDatabase database;
  private final ApodService nasa;
  private final Executor networkPool;

  private static Application context;

  private ApodRepository() {
    if (context == null) {
      throw new IllegalStateException();
    }
    database = ApodDatabase.getInstance();
    nasa = ApodService.getInstance();
    // Sets so many threads. If no threads open, goes in que.
    networkPool = Executors.newFixedThreadPool(NETWORK_THREAD_COUNT);
  }

  public static void setContext(Application context) {
    ApodRepository.context = context;
  }

  public static ApodRepository getInstance() {
    return InstanceHolder.INSTANCE;
  }

  //
  public Single<Apod> get(Date date) {
    ApodDao dao = database.getApodDao();
    return dao.select(date)
        // Runs on background thread.
        .subscribeOn(Schedulers.io())
        // If empty result. Provides other task if empty.
        .switchIfEmpty((SingleSource<? extends Apod>) (observer) ->
            // Gets url from nasa. Fails if we get a date that is not there. Crashes.
            nasa.get(BuildConfig.API_KEY, ApodService.DATE_FORMATTER.format(date))
                // Does on different thread. Pool of threads, limited in size.
                .subscribeOn(Schedulers.from(networkPool))
                // Single result, return same thing or new. Sets ID and returns apod object.
                .flatMap((apod) ->
                    dao.insert(apod)
                        .map((id) -> {
                          apod.setId(id);
                          return apod;
                        })
                )
                .subscribe(observer)
        )
        // However we get apod object, invokes insert access.
        .doAfterSuccess(this::insertAccess);
  }

  public LiveData<List<ApodWithStats>> get() {
    return database.getApodDao().selectWithStats();
  }

  public Single<String> getImage(@NonNull Apod apod) {
    // TODO Add local file download & reference.
    boolean canBeLocal = (apod.getMediaType() == MediaType.IMAGE);
    File file = canBeLocal ? getFile(apod) : null;
    return Single.fromCallable(apod::getUrl);
  }

  // Construct file name from apod object
  private File getFile(@NonNull Apod apod) {
    String url = apod.getUrl();
    File file = null;
    Matcher matcher = URL_FILENAME_PATTERN.matcher(url);
    if (matcher.matches()) {
      // One is date and file name matched, two parameters. Catcher group 1. getdate is param 1, matcher is 2.
      String filename = String.format(LOCAL_FILENAME_FORMAT, apod.getDate(), matcher.group(1));
      // Stores this, external storage. Private to app. Stores in internal storage if no space.
      // Stores pictures, directory.
      File directory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
      // Checks if unavailable, stores elsewhere. If directory not equal to media mounted, then new directory.
      if (! Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(directory))) {
        // Internal storage.
        directory = context.getFilesDir();
      }
      file = new File(directory, filename);
    }
    return file;
  }

  private void insertAccess(Apod apod) {
    AccessDao accessDao = database.getAccessDao();
    Access access = new Access();
    access.setApodId(apod.getId());
    accessDao.insert(access)
        .subscribeOn(Schedulers.io())
        .subscribe(/* TODO Handle error result */);
  }

  private static class InstanceHolder {

    private static final ApodRepository INSTANCE = new ApodRepository();

  }

}
