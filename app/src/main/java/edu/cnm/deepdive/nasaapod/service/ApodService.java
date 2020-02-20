package edu.cnm.deepdive.nasaapod.service;

import android.annotation.SuppressLint;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.cnm.deepdive.nasaapod.BuildConfig;
import edu.cnm.deepdive.nasaapod.model.entity.Apod;
import io.reactivex.Single;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.Url;

public interface ApodService {

  String DATE_FORMAT = "yyyy-MM-dd";

  @SuppressLint("SimpleDateFormat")
  DateFormat DATE_FORMATTER = new SimpleDateFormat(DATE_FORMAT);

  static ApodService getInstance() {
    return InstanceHolder.INSTANCE;
  }

  @GET("planetary/apod")
  Single<Apod> get(@Query("api_key") String apiKey, @Query("date") String date);

  // Retrofit annotation, request pulls bytes of image
  @GET
  Single<ResponseBody> getFile(@Url String url);

  class InstanceHolder {

    private static final ApodService INSTANCE;

    static {
      Gson gson = new GsonBuilder()
          .excludeFieldsWithoutExposeAnnotation()
          .setDateFormat(DATE_FORMAT)
          .create();
      OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
          .connectTimeout(BuildConfig.HTTP_CONNECT_TIMEOUT, TimeUnit.SECONDS)
          .readTimeout(BuildConfig.HTTP_READ_TIMEOUT, TimeUnit.SECONDS);
      if (BuildConfig.HTTP_LOG_LEVEL != Level.NONE) {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        interceptor.setLevel(BuildConfig.HTTP_LOG_LEVEL);
        clientBuilder.addInterceptor(interceptor);
      }
      OkHttpClient client = clientBuilder.build();
      Retrofit retrofit = new Retrofit.Builder()
          .addConverterFactory(GsonConverterFactory.create(gson))
          .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
          .client(client)
          .baseUrl(BuildConfig.BASE_URL)
          .build();
      INSTANCE = retrofit.create(ApodService.class);
    }

  }

}
