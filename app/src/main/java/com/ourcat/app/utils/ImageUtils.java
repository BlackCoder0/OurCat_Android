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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ImageUtils {

    public static File uriToFile(Context context, Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null)
                return null;

            File tempFile = File.createTempFile("upload_img_" + System.currentTimeMillis(), ".jpg",
                    context.getCacheDir());
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
            inputStream.close();
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static File uriToTempFile(Context context, Uri uri) throws IOException {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        File tempFile = File.createTempFile("temp_image", ".jpg", context.getCacheDir());
        OutputStream outputStream = new FileOutputStream(tempFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.close();
        inputStream.close();
        return tempFile;
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
