package com.comp90018.storystep.platform.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.comp90018.storystep.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The walker's avatar: one photograph of their choosing, kept as the app's own file.
 *
 * <p>Chosen through the photo picker, copied once at a size a disc can show, and read back by
 * every avatar on screen - the profile, the journey posts. Removing it is deleting the file;
 * without one the drawn placeholder stands in. Nothing about it leaves the phone.
 */
public final class ProfileAvatar {

    private static final int EDGE_PX = 512;
    private static final int JPEG_QUALITY = 90;

    private ProfileAvatar() {
    }

    public static File file(Context context) {
        return new File(new File(context.getFilesDir(), "profile"), "avatar.jpg");
    }

    public static boolean exists(Context context) {
        return file(context).isFile();
    }

    /** Copies the chosen photo in, square-cropped and scaled. Blocking; returns whether it took. */
    public static boolean set(Context context, Uri photo) {
        Bitmap bitmap = decode(context, photo);
        if (bitmap == null) {
            return false;
        }
        int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap square = Bitmap.createBitmap(bitmap,
                (bitmap.getWidth() - side) / 2, (bitmap.getHeight() - side) / 2, side, side);
        Bitmap scaled = side > EDGE_PX ? Bitmap.createScaledBitmap(square, EDGE_PX, EDGE_PX, true) : square;
        File target = file(context);
        //noinspection ResultOfMethodCallIgnored
        target.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(target)) {
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            return true;
        } catch (IOException unwritable) {
            return false;
        } finally {
            if (scaled != square) {
                scaled.recycle();
            }
            if (square != bitmap) {
                square.recycle();
            }
            bitmap.recycle();
        }
    }

    public static void clear(Context context) {
        //noinspection ResultOfMethodCallIgnored
        file(context).delete();
    }

    /** Shows the avatar in the view, as a disc, or the placeholder when there is none. */
    public static void bind(ImageView view) {
        File avatar = file(view.getContext());
        if (avatar.isFile()) {
            Glide.with(view)
                    .load(avatar)
                    // The file name never changes; its modification time tells Glide it did.
                    .signature(new ObjectKey(avatar.lastModified()))
                    .circleCrop()
                    .placeholder(R.drawable.ic_avatar)
                    .into(view);
        } else {
            Glide.with(view).clear(view);
            view.setImageResource(R.drawable.ic_avatar);
        }
    }

    private static Bitmap decode(Context context, Uri photo) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(photo)) {
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (IOException | SecurityException unreadable) {
            return null;
        }
        int shortEdge = Math.min(bounds.outWidth, bounds.outHeight);
        if (shortEdge <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (shortEdge / options.inSampleSize > EDGE_PX * 2) {
            options.inSampleSize *= 2;
        }
        try (InputStream in = context.getContentResolver().openInputStream(photo)) {
            return BitmapFactory.decodeStream(in, null, options);
        } catch (IOException | SecurityException unreadable) {
            return null;
        }
    }
}
