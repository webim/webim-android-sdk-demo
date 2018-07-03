package com.webimapp.android.demo.client;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.ortiz.touchview.TouchImageView;

public class ImageActivity extends AppCompatActivity {
    private Uri imageUri = null;

    private final int MENU_OPEN_IN_BROWSER_ID = Menu.FIRST;
    private final int MENU_COPY_URL_ID = Menu.FIRST + 1;
    private final int MENU_SAVE_IMG_ID = Menu.FIRST + 2;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        final TouchImageView imageView = findViewById(R.id.imageView);
        imageView.setVisibility(View.VISIBLE);

        Intent callingActivityIntent = getIntent();
        if(callingActivityIntent != null) {
            imageUri = callingActivityIntent.getData();
            if(imageUri != null) {
                Glide.with(this).load(imageUri).asBitmap().into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource,
                            GlideAnimation<? super Bitmap> glideAnimation) {
                        imageView.setImageBitmap(resource);
                    }
                });
            }
        }
    }

    private void showMessage(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
    }

    private void showMessage(int messageId) {
        Toast toast = Toast.makeText(this, getString(messageId), Toast.LENGTH_SHORT);
        toast.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(menu.FIRST, MENU_OPEN_IN_BROWSER_ID, MENU_OPEN_IN_BROWSER_ID, R.string.open_in_browser);
        menu.add(menu.FIRST, MENU_COPY_URL_ID, MENU_COPY_URL_ID, R.string.copy_url);
        menu.add(menu.FIRST, MENU_SAVE_IMG_ID, MENU_SAVE_IMG_ID, R.string.save_image);
        return true;
    }

    private void downloadImage() {
        String imgName = getString(R.string.def_image_name, System.currentTimeMillis());
        showMessage(getString(R.string.saving_image, imgName));

        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            DownloadManager.Request request = new DownloadManager.Request(imageUri);
            request.setTitle(imgName);
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, imgName);

            manager.enqueue(request);
        } else {
            showMessage(getString(R.string.saving_failed));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_OPEN_IN_BROWSER_ID:
                Intent intent = new Intent(Intent.ACTION_VIEW, imageUri);
                startActivity(intent);
                break;
            case MENU_COPY_URL_ID:
                ClipData clip = ClipData.newUri(getContentResolver(), "URI", imageUri);
                ClipboardManager clipboard
                        = (ClipboardManager) this.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    showMessage(R.string.copied_url);
                } else {
                    showMessage(R.string.copy_failed);
                }
                break;
            case MENU_SAVE_IMG_ID:
                if (Build.VERSION.SDK_INT >= 23
                        && ActivityCompat.checkSelfPermission(
                                this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                            this,
                            new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE },
                            1);
                } else {
                    downloadImage();
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            downloadImage();
        }
    }
}
