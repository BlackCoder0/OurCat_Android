package com.ourcat.app.utils;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ImageUtils {

    public static File uriToFile(Context context, Uri uri) {
        try {
            return uriToTempFile(context, uri);
        } catch (IOException e) {
            return null;
        }
    }

    public static File uriToTempFile(Context context, Uri uri) throws IOException {
        return compressUriForUpload(context, uri, 2560, 92);
    }

    public static File compressUriForUpload(Context context, Uri uri, int maxLongSide, int quality) throws IOException {
        Bitmap bitmap = decodeBitmap(context, uri, maxLongSide);
        if (bitmap == null) {
            throw new IOException("decode bitmap failed");
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longSide = Math.max(width, height);
        Bitmap uploadBitmap = bitmap;

        if (longSide > maxLongSide && maxLongSide > 0) {
            float scale = maxLongSide / (float) longSide;
            int targetWidth = Math.max(1, Math.round(width * scale));
            int targetHeight = Math.max(1, Math.round(height * scale));
            uploadBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
            if (uploadBitmap != bitmap) {
                bitmap.recycle();
            }
        }

        File tempFile = File.createTempFile("upload_compressed_", ".jpg", context.getCacheDir());
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            if (!uploadBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)) {
                throw new IOException("Cannot encode upload image");
            }
            outputStream.flush();
        } finally {
            uploadBitmap.recycle();
        }
        return tempFile;
    }

    private static Bitmap decodeBitmap(Context context, Uri uri, int maxLongSide) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                if (maxLongSide > 0) {
                    int srcWidth = info.getSize().getWidth();
                    int srcHeight = info.getSize().getHeight();
                    int longSide = Math.max(srcWidth, srcHeight);
                    if (longSide > maxLongSide) {
                        float scale = maxLongSide / (float) longSide;
                        decoder.setTargetSize(
                                Math.max(1, Math.round(srcWidth * scale)),
                                Math.max(1, Math.round(srcHeight * scale)));
                    }
                }
            });
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(inputStream, null, bounds);
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxLongSide);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(inputStream, null, options);
        }
    }

    private static int calculateInSampleSize(int width, int height, int maxLongSide) {
        if (maxLongSide <= 0) {
            return 1;
        }
        int inSampleSize = 1;
        int longSide = Math.max(width, height);
        while (longSide / inSampleSize > maxLongSide * 2) {
            inSampleSize *= 2;
        }
        return Math.max(1, inSampleSize);
    }

    public static void saveImage(Context context, String imageUrl) {
        new Thread(() -> {
            try {
                // 下载原始文件
                File file = Glide.with(context)
                        .downloadOnly()
                        .load(imageUrl)
                        .submit()
                        .get();

                // 确定文件扩展名
                String extension = "jpg"; // 默认
                if (imageUrl.contains(".")) {
                    String ext = imageUrl.substring(imageUrl.lastIndexOf(".") + 1).toLowerCase();
                    if (ext.startsWith("png"))
                        extension = "png";
                    else if (ext.startsWith("gif"))
                        extension = "gif";
                    else if (ext.startsWith("webp"))
                        extension = "webp";
                }

                String fileName = "IMG_" + System.currentTimeMillis() + "." + extension;
                String mimeType = "image/" + (extension.equals("jpg") ? "jpeg" : extension);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OurCat");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);

                    Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        try (InputStream in = new java.io.FileInputStream(file);
                                OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }
                        values.clear();
                        values.put(MediaStore.Images.Media.IS_PENDING, 0);
                        context.getContentResolver().update(uri, values, null, null);

                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(() -> Toast.makeText(context, "图片已保存", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    File destFile = new File(picturesDir, fileName);

                    try (InputStream in = new java.io.FileInputStream(file);
                            OutputStream out = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    // 通知媒体库扫描文件
                    android.media.MediaScannerConnection.scanFile(context,
                            new String[] { destFile.getAbsolutePath() },
                            new String[] { mimeType }, null);

                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> Toast.makeText(context, "图片已保存", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> Toast.makeText(context, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private static void saveBitmapToGallery(Context context, Bitmap bitmap) {
        // Method no longer used but kept if needed for other purposes, or can be
        // removed.
        // For this task, we replaced the main logic in saveImage.
    }
}
