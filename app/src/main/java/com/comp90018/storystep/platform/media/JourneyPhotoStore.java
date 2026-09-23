package com.comp90018.storystep.platform.media;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The photographs a user attaches to a walk, kept as the app's own files.
 *
 * <p>A picker grants access to a photo only for the moment; a URI kept in the database would be
 * dead by the next launch. So each chosen photo is copied once, resized to something a card can
 * show, into {@code files/journeys/<session id>/}, and the session row records only the file
 * names. Deleting a walk deletes its directory.
 */
public final class JourneyPhotoStore {

    /** Long edge of a stored copy. A card is at most a few hundred dp wide; this is plenty. */
    private static final int MAX_EDGE_PX = 1600;
    private static final int JPEG_QUALITY = 86;

    private JourneyPhotoStore() {
    }

    public static File directory(Context context, String sessionId) {
        return new File(new File(context.getFilesDir(), "journeys"), sessionId);
    }

    public static File file(Context context, String sessionId, String name) {
        return new File(directory(context, sessionId), name);
    }

    /**
     * What to load for a stored photo entry: the file under the session's directory, or the
     * URL itself when the entry is one - the bundled sample walks point at Commons.
     */
    public static Object source(Context context, String sessionId, String entry) {
        return entry.startsWith("http://") || entry.startsWith("https://")
                ? entry : file(context, sessionId, entry);
    }

    /**
     * Copies each photo into the session's directory and returns the stored names, in order.
     * A photo that cannot be read is skipped rather than failing the batch. Blocking; call
     * from a background executor.
     */
    public static List<String> importAll(Context context, String sessionId, List<Uri> photos) {
        File directory = directory(context, sessionId);
        //noinspection ResultOfMethodCallIgnored
        directory.mkdirs();
        List<String> names = new ArrayList<>();
        for (Uri photo : photos) {
            String name = importOne(context.getContentResolver(), directory, photo);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * A file in the session's directory for the camera to write into, created empty. The
     * caller passes its content URI to the camera and, if the capture succeeds, records the
     * returned name with the walk's other photographs.
     */
    public static java.io.File newCapture(Context context, String sessionId) {
        File directory = directory(context, sessionId);
        //noinspection ResultOfMethodCallIgnored
        directory.mkdirs();
        return new File(directory, UUID.randomUUID().toString() + ".jpg");
    }

    /** The names of the photographs already in the session's directory, oldest first. */
    public static List<String> names(Context context, String sessionId) {
        File[] files = directory(context, sessionId).listFiles();
        List<String> names = new ArrayList<>();
        if (files == null) {
            return names;
        }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File file : files) {
            names.add(file.getName());
        }
        return names;
    }

    /** Removes every session's photographs. */
    public static void deleteEverything(Context context) {
        File root = new File(context.getFilesDir(), "journeys");
        deleteRecursively(root);
    }

    /** Removes the session's directory and everything in it. Safe when there is none. */
    public static void deleteAll(Context context, String sessionId) {
        deleteRecursively(directory(context, sessionId));
    }

    /**
     * Recursively removes one file or directory.
     *
     * <p>A failed deletion is reported instead of silently leaving private
     * photographs behind.
     */
    private static void deleteRecursively(File target) {
        if (!target.exists()) {
            return;
        }

        if (target.isDirectory()) {
            File[] children = target.listFiles();

            if (children == null) {
                throw new IllegalStateException(
                        "Unable to inspect photo directory: " + target);
            }

            for (File child : children) {
                deleteRecursively(child);
            }
        }

        if (!target.delete() && target.exists()) {
            throw new IllegalStateException(
                    "Unable to delete session artifact: " + target);
        }
    }

    @Nullable
    private static String importOne(ContentResolver resolver, File directory, Uri photo) {
        Bitmap bitmap = decodeScaled(resolver, photo);
        if (bitmap == null) {
            return null;
        }
        String name = UUID.randomUUID().toString() + ".jpg";
        try (OutputStream out = new FileOutputStream(new File(directory, name))) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            return name;
        } catch (IOException unreadable) {
            return null;
        } finally {
            bitmap.recycle();
        }
    }

    /** Decodes with a sample size that lands the long edge at or under {@link #MAX_EDGE_PX}. */
    @Nullable
    private static Bitmap decodeScaled(ContentResolver resolver, Uri photo) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(photo)) {
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (IOException | SecurityException unreadable) {
            return null;
        }
        int longEdge = Math.max(bounds.outWidth, bounds.outHeight);
        if (longEdge <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (longEdge / options.inSampleSize > MAX_EDGE_PX) {
            options.inSampleSize *= 2;
        }
        try (InputStream in = resolver.openInputStream(photo)) {
            return BitmapFactory.decodeStream(in, null, options);
        } catch (IOException | SecurityException unreadable) {
            return null;
        }
    }
}
