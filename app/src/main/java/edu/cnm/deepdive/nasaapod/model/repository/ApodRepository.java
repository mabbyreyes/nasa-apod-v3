package edu.cnm.deepdive.nasaapod.model.repository;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.provider.MediaStore.Audio.Media;
import android.provider.MediaStore.MediaColumns;
import android.webkit.MimeTypeMap;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
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
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.schedulers.Schedulers;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.ResponseBody;

public class ApodRepository {

  private static final int NETWORK_THREAD_COUNT = 10;
  // Regex string to pull file name and give it name with date.
  private static final Pattern URL_FILENAME_PATTERN =
      Pattern.compile("^.*/([^/#?]+)(?:\\?.*)?(?:#.*)?$");
  // % placeholder for first parameter, t is date or time, Y is 4 digit year, m is two digit month, d is two digit,
  private static final String LOCAL_FILENAME_FORMAT = "%1$tY%1$tm%1$td-%2$s";
  private static final String MEDIA_RECORD_FAILURE = "Unable to create MediaStore record.";

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
    return Maybe.fromCallable(() ->
        canBeLocal ? (file.exists() ? file.toURI().toString() : null) : apod.getUrl()
         )
        .switchIfEmpty((SingleSource<String>) (observer) ->
            nasa.getFile(apod.getUrl())
            .map((body) -> {
              try {
                return download(body, file);
              } catch (IOException ex) {
                return apod.getUrl();
              }
            })
            .subscribeOn(Schedulers.from(networkPool))
            .subscribe(observer)
        );
  }

  public Completable downloadImage(@NonNull Apod apod) {
    if (apod.getMediaType() != MediaType.IMAGE) {
      throw new IllegalArgumentException();
    }
    String url = (apod.getHdUrl() != null) ? apod.getHdUrl() : apod.getHdUrl();
    return nasa.getFile(url)
        .subscribeOn(Schedulers.from(networkPool))
        .map((body) -> {
          ContentResolver resolver = context.getContentResolver();
          Uri uri = getMediaUri(resolver, url, apod.getTitle());
          try (
              InputStream input = body.byteStream();
              OutputStream output = resolver.openOutputStream(uri);
              ) {
            // Invokes.
            copy(input, output);
          } catch (IOException ex) {
            resolver.delete(uri, null, null);
            // TODO Throw new exception?
          }
          return true;
        })
        .ignoreElement();
  }

  private Uri getMediaUri(@NonNull ContentResolver resolver, @NonNull String sourceUrl,
      @NonNull String title) throws IOException {
    String extension = MimeTypeMap.getFileExtensionFromUrl(sourceUrl);
    MimeTypeMap map = MimeTypeMap.getSingleton();
    String mimeType = map.getMimeTypeFromExtension(extension);
    ContentValues contentValues = new ContentValues();
    contentValues.put(MediaColumns.DISPLAY_NAME, title);
    contentValues.put(MediaColumns.MIME_TYPE, mimeType);
    if (VERSION.SDK_INT >= VERSION_CODES.Q) {
      contentValues.put(MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
    }
    Uri uri = resolver.insert(Media.EXTERNAL_CONTENT_URI, contentValues);
    if (uri == null) {
      throw new IOException(MEDIA_RECORD_FAILURE);
    }
    return uri;
  }

  private long copy(InputStream input, OutputStream output) throws IOException {
    byte[] buffer = new byte[16_384];
    long totalBytes = 0;
    int bytesRead;
    do {
      if ((bytesRead = input.read(buffer)) > 0) {
        output.write(buffer, 0, bytesRead);
        totalBytes += bytesRead;
      }
    } while (bytesRead >= 0);
    return totalBytes;
  }

  // Download method.
  private String download(ResponseBody body, File file) throws IOException {
    try (
        // Requesting image, recieving bytes.
        InputStream input = body.byteStream();
        // Opens file for output. Writes bytes to.
        OutputStream output = new FileOutputStream(file);
        ) {
      // Read bytes from NASA, writes to server. -1 and done.
      byte[] buffer = new byte[16_384];
      int bytesRead = 0;
      while (bytesRead >= 0) {
        bytesRead = input.read(buffer);
        if (bytesRead > 0) {
          output.write(buffer, 0, bytesRead);
        }
      }
      // Gets put in operating system. Flush writes them out.
      output.flush();
      return file.toURI().toString();
    }
  }

    // Construct file name from apod object
    private File getFile (@NonNull Apod apod){
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
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(directory))) {
          // Internal storage.
          directory = context.getFilesDir();
        }
        file = new File(directory, filename);
      }
      return file;
    }

    private void insertAccess (Apod apod){
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


