package edu.cnm.deepdive.nasaapod;

import android.app.Application;
import com.facebook.stetho.Stetho;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.squareup.picasso.Picasso;
import edu.cnm.deepdive.nasaapod.model.repository.ApodRepository;
import edu.cnm.deepdive.nasaapod.service.ApodDatabase;
import edu.cnm.deepdive.nasaapod.service.GoogleSignInRepository;
import io.reactivex.schedulers.Schedulers;
import okhttp3.logging.HttpLoggingInterceptor.Level;

public class ApodApplication extends Application {

  @Override
  public void onCreate() {
    super.onCreate();
    Stetho.initializeWithDefaults(this);
    Picasso.setSingletonInstance(
        new Picasso.Builder(this)
            .loggingEnabled(BuildConfig.HTTP_LOG_LEVEL != Level.NONE)
            .build()
    );
    GoogleSignInRepository.setContext(this);
    ApodDatabase.setContext(this);
    ApodRepository.setContext(this);
    ApodDatabase.getInstance().getApodDao().delete()
        .subscribeOn(Schedulers.io())
        .subscribe();
  }

}
