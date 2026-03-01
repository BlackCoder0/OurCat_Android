package com.ourcat.app.utils;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.OutputStream;

public class ImageUtils {

    public static void saveImage(Context context, String imageUrl) {
        Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                        saveBitmapToGallery(context, resource);
                    }
                });
    }

    private static void saveBitmapToGallery(Context context, Bitmap bitmap) {
        String fileName = "IMG_" + System.currentTimeMillis() + ".jpg";
        OutputStream fos;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OurCat");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    fos = context.getContentResolver().openOutputStream(uri);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    if (fos != null) fos.close();

                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    context.getContentResolver().update(uri, values, null, null);
                    Toast.makeText(context, "图片已保存", Toast.LENGTH_SHORT).show();
                }
            } else {
                // For Android 9 and below, this is simplified.
                // In a real production app, you might need more robust handling for older versions.
                // But given minSdk is 24, scoped storage is the main concern.
                String imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
                java.io.File image = new java.io.File(imagesDir, fileName);
                fos = new java.io.FileOutputStream(image);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();
                MediaStore.Images.Media.insertImage(context.getContentResolver(), image.getAbsolutePath(), image.getName(), image.getName());
                Toast.makeText(context, "图片已保存", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
