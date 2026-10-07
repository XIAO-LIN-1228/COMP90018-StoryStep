package com.comp90018.storystep.ui;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.comp90018.storystep.data.remote.ApiClientFactory;

import java.io.InputStream;

/**
 * Sends every Glide download through the app's own OkHttp client, so photographs are fetched
 * with the same identifying User-Agent as the API calls that found them.
 *
 * <p>Callers keep using {@code Glide.with(...)}; the annotation processor wires this module in.
 */
@GlideModule
public final class StoryStepGlideModule extends AppGlideModule {

    @Override
    public void registerComponents(@NonNull Context context,
                                   @NonNull Glide glide,
                                   @NonNull Registry registry) {
        registry.replace(GlideUrl.class, InputStream.class,
                new OkHttpUrlLoader.Factory(ApiClientFactory.createImageClient()));
    }

    /** Only this module is registered; manifest parsing is a Glide 3 leftover. */
    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
