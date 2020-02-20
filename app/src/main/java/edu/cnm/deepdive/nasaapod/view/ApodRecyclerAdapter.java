package edu.cnm.deepdive.nasaapod.view;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.squareup.picasso.Picasso;
import edu.cnm.deepdive.nasaapod.R;
import edu.cnm.deepdive.nasaapod.model.entity.Apod;
import edu.cnm.deepdive.nasaapod.model.entity.Apod.MediaType;
import edu.cnm.deepdive.nasaapod.model.pojo.ApodWithStats;
import edu.cnm.deepdive.nasaapod.view.ApodRecyclerAdapter.Holder;
import io.reactivex.functions.Consumer;
import java.util.List;

public class ApodRecyclerAdapter extends RecyclerView.Adapter<Holder> {

  private final Context context;
  private final List<ApodWithStats> apods;
  private final OnClickListener listener;
  private final ThumbnailResolver resolver;

  // code used when needing URL for single item.
  public ApodRecyclerAdapter(Context context, List<ApodWithStats> apods,
      OnClickListener listener, ThumbnailResolver resolver) {
    this.context = context;
    this.apods = apods;
    this.listener = (listener != null) ? listener : (v, apod, pos) -> {};
    this.resolver = resolver;
  }


  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(context).inflate(R.layout.item_apod, parent, false);
    return new Holder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull Holder holder, int position) {
    ApodWithStats apod = apods.get(position);
    holder.bind(position, apod);
  }

  @Override
  public int getItemCount() {
    return apods.size();
  }

  class Holder extends RecyclerView.ViewHolder {

    private final View view;
    private final ImageView thumbnail;
    private final TextView title;
    private final TextView date;
    private final TextView access;

    private Holder(@NonNull View view) {
      super(view);
      this.view = view;
      thumbnail = view.findViewById(R.id.thumbnail);
      title = view.findViewById(R.id.title);
      date = view.findViewById(R.id.date);
      access = view.findViewById(R.id.access);
    }

    private void bind(int position, ApodWithStats apod) {
      title.setText(apod.getApod().getTitle());
      date.setText(DateFormat.getMediumDateFormat(context).format(apod.getApod().getDate()));
      String countQuantity = context.getResources()
          .getQuantityString(R.plurals.access_count, apod.getAccessCount());
      access.setText(context.getString(R.string.access_format,
          apod.getAccessCount(),
          DateFormat.getMediumDateFormat(context).format(apod.getLastAccess()),
          countQuantity));
      // Returns one instance of Picasso.
      Picasso picasso = Picasso.get();
      // if not image, uses play button image holder.
      if (apod.getApod().getMediaType() == MediaType.IMAGE) {
        if (resolver != null) {
          // When it finds URL, take it and place in picasso then load in thumbnail.
          resolver.apply(apod.getApod(), (path) -> picasso.load(path).into(thumbnail));
        } else {
          picasso.load(apod.getApod().getUrl()).into(thumbnail);
        }
      } else {
        picasso.load(R.drawable.ic_slow_motion_video).into(thumbnail);
      }
      thumbnail.setContentDescription(apod.getApod().getTitle());
      view.setOnClickListener((v) -> listener.onClick(v, apod.getApod(), position));
    }

  }

  @FunctionalInterface
  public interface OnClickListener {

    void onClick(View view, Apod apod, int position);

  }
    // functional interface has one implemented method.
  @FunctionalInterface
  public interface ThumbnailResolver {
    // Thumbnail resolver, adapter doesnt have to know about viewmodel.
    void apply(Apod apod, Consumer<String> consumer);

  }

}
