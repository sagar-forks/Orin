package com.alium.orin.appwidgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import com.alium.orin.glide.SongGlideRequest;
import com.alium.orin.model.Song;
import com.alium.orin.ui.activities.MainActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;
import com.kabouzeid.appthemehelper.util.MaterialValueHelper;
import com.alium.orin.R;
import com.alium.orin.service.MusicService;
import com.alium.orin.util.Util;

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
public class AppWidgetSmall extends BaseAppWidget {
    public static final String NAME = "app_widget_small";

    private static AppWidgetSmall mInstance;
    private Target<Bitmap> target; // for cancellation

    public static synchronized AppWidgetSmall getInstance() {
        if (mInstance == null) {
            mInstance = new AppWidgetSmall();
        }
        return mInstance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpdate(final Context context, final AppWidgetManager appWidgetManager,
                         final int[] appWidgetIds) {
        defaultAppWidget(context, appWidgetIds);
        final Intent updateIntent = new Intent(MusicService.APP_WIDGET_UPDATE);
        updateIntent.putExtra(MusicService.EXTRA_APP_WIDGET_NAME, NAME);
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        updateIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        context.sendBroadcast(updateIntent);
    }

    /**
     * Initialize given widgets to default state, where we launch Music on
     * default click and hide actions if service not running.
     */
    private void defaultAppWidget(final Context context, final int[] appWidgetIds) {
        final RemoteViews appWidgetView = new RemoteViews(context.getPackageName(), R.layout.app_widget_small);

        appWidgetView.setViewVisibility(R.id.media_titles, View.INVISIBLE);
        appWidgetView.setViewVisibility(R.id.image, View.INVISIBLE);
        appWidgetView.setImageViewBitmap(R.id.button_next, createBitmap(Util.getTintedVectorDrawable(context, R.drawable.ic_skip_next_white_24dp, MaterialValueHelper.getSecondaryTextColor(context, false)), 1f));
        appWidgetView.setImageViewBitmap(R.id.button_prev, createBitmap(Util.getTintedVectorDrawable(context, R.drawable.ic_skip_previous_white_24dp, MaterialValueHelper.getSecondaryTextColor(context, false)), 1f));
        appWidgetView.setImageViewBitmap(R.id.button_toggle_play_pause, createBitmap(Util.getTintedVectorDrawable(context, R.drawable.ic_play_arrow_white_24dp, MaterialValueHelper.getSecondaryTextColor(context, false)), 1f));

        linkButtons(context, appWidgetView);
        pushUpdate(context, appWidgetIds, appWidgetView);
    }

    private void pushUpdate(final Context context, final int[] appWidgetIds, final RemoteViews views) {
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        if (appWidgetIds != null) {
            appWidgetManager.updateAppWidget(appWidgetIds, views);
        } else {
            appWidgetManager.updateAppWidget(new ComponentName(context, getClass()), views);
        }
    }

    /**
     * Check against {@link AppWidgetManager} if there are any instances of this
     * widget.
     */
    private boolean hasInstances(final Context context) {
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        final int[] mAppWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context,
                getClass()));
        return mAppWidgetIds.length > 0;
    }

    /**
     * Handle a change notification coming over from
     * {@link MusicService}
     */
    public void notifyChange(final MusicService service, final String what) {
        if (hasInstances(service)) {
            if (MusicService.META_CHANGED.equals(what) || MusicService.PLAY_STATE_CHANGED.equals(what)) {
                performUpdate(service, null);
            }
        }
    }

    /**
     * Update all active widget instances by pushing changes
     */
    public void performUpdate(final MusicService service, final int[] appWidgetIds) {
        final RemoteViews appWidgetView = new RemoteViews(service.getPackageName(), R.layout.app_widget_small);

        final boolean isPlaying = service.isPlaying();
        final Song song = service.getCurrentSong();

        // Set the titles and artwork
        if (TextUtils.isEmpty(song.title) && TextUtils.isEmpty(song.artistName)) {
            appWidgetView.setViewVisibility(R.id.media_titles, View.INVISIBLE);
        } else {
            if (TextUtils.isEmpty(song.title) || TextUtils.isEmpty(song.artistName)) {
                appWidgetView.setTextViewText(R.id.text_separator, "");
            } else {
                appWidgetView.setTextViewText(R.id.text_separator, "•");
            }

            appWidgetView.setViewVisibility(R.id.media_titles, View.VISIBLE);
            appWidgetView.setTextViewText(R.id.title, song.title);
            appWidgetView.setTextViewText(R.id.text, song.artistName);
        }

        // Set correct drawable for pause state
        int playPauseRes = isPlaying ? R.drawable.ic_pause_white_24dp : R.drawable.ic_play_arrow_white_24dp;
        appWidgetView.setImageViewBitmap(R.id.button_toggle_play_pause, createBitmap(Util.getTintedVectorDrawable(service, playPauseRes, MaterialValueHelper.getSecondaryTextColor(service, false)), 1f));

        // Set prev/next button drawables
        appWidgetView.setImageViewBitmap(R.id.button_next, createBitmap(Util.getTintedVectorDrawable(service, R.drawable.ic_skip_next_white_24dp, MaterialValueHelper.getSecondaryTextColor(service, false)), 1f));
        appWidgetView.setImageViewBitmap(R.id.button_prev, createBitmap(Util.getTintedVectorDrawable(service, R.drawable.ic_skip_previous_white_24dp, MaterialValueHelper.getSecondaryTextColor(service, false)), 1f));

        // Link actions buttons to intents
        linkButtons(service, appWidgetView);

        // Load the album cover async and push the update on completion
        final Context appContext = service.getApplicationContext();
        final int widgetImageSize = service.getResources().getDimensionPixelSize(R.dimen.app_widget_small_image_size);
        service.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (target != null) {
                    Glide.clear(target);
                }
                target = SongGlideRequest.Builder.from(Glide.with(appContext), song)
                        .checkIgnoreMediaStore(appContext)
                        .asBitmap().build()
                        .into(new SimpleTarget<Bitmap>(widgetImageSize, widgetImageSize) {
                            @Override
                            public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                                update(resource);
                            }

                            @Override
                            public void onLoadFailed(Exception e, Drawable errorDrawable) {
                                super.onLoadFailed(e, errorDrawable);
                                update(null);
                            }

                            private void update(@Nullable Bitmap bitmap) {
                                appWidgetView.setViewVisibility(R.id.image, View.VISIBLE);
                                if (bitmap == null) {
                                    appWidgetView.setImageViewResource(R.id.image, R.drawable.default_album_art);
                                } else {
                                    appWidgetView.setImageViewBitmap(R.id.image, bitmap);
                                }
                                pushUpdate(appContext, appWidgetIds, appWidgetView);
                            }
                        });
            }
        });
    }

    /**
     * Link up various button actions using {@link PendingIntent}.
     */
    private void linkButtons(final Context context, final RemoteViews views) {
        Intent action;
        PendingIntent pendingIntent;

        final ComponentName serviceName = new ComponentName(context, MusicService.class);

        // Home
        action = new Intent(context, MainActivity.class);
        action.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        pendingIntent = PendingIntent.getActivity(context, 0, action, 0);
        views.setOnClickPendingIntent(R.id.image, pendingIntent);
        views.setOnClickPendingIntent(R.id.media_titles, pendingIntent);

        // Previous track
        pendingIntent = buildPendingIntent(context, MusicService.ACTION_REWIND, serviceName);
        views.setOnClickPendingIntent(R.id.button_prev, pendingIntent);

        // Play and pause
        pendingIntent = buildPendingIntent(context, MusicService.ACTION_TOGGLE_PAUSE, serviceName);
        views.setOnClickPendingIntent(R.id.button_toggle_play_pause, pendingIntent);

        // Next track
        pendingIntent = buildPendingIntent(context, MusicService.ACTION_SKIP, serviceName);
        views.setOnClickPendingIntent(R.id.button_next, pendingIntent);
    }
}
