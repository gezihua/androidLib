package com.develop.androidlib.bitmap;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @date 2013-9-6
 */
public class LoadIconTask implements Runnable {

    ImageView imageView;
    BitmapLoader loader;
    ActivityInfo info;
    String memoryCacheKey;

    static Handler handler = new Handler(Looper.getMainLooper());


    public LoadIconTask(ImageView imageView, BitmapLoader loader, ActivityInfo info, String key) {
        super();
        this.imageView = imageView;
        this.loader = loader;
        this.info = info;
        this.memoryCacheKey = key;
        loader.prepareDisplayTaskFor(imageView,memoryCacheKey);
    }


    @Override
    public void run() {
        if (waitIfPaused())
            return;
        if (delayIfNeed())
            return;
        if (isCancled())
            return;
        Bitmap db = loader.getBitmapFromCache(memoryCacheKey);
        if (db == null) {
            Drawable drawable = info.loadIcon(imageView.getContext().getPackageManager());
            //由于图标不一定都是BitmapDrawable,比如nubia的TimeDrawable,这些就不进行缓存了
            if(drawable instanceof BitmapDrawable){
                db = ((BitmapDrawable) drawable).getBitmap();
                if (db != null && !db.isRecycled()) {
                    CacheUtil.ImageSize aDefault = CacheUtil.ImageSize.getDefault();
                    if(db.getWidth() > aDefault.getWidth() || db.getHeight() > aDefault.getHeight()){
                        Bitmap bitmap = Bitmap.createScaledBitmap(db,
                                aDefault.getWidth(),aDefault.getHeight(),false);
                        db.recycle();
                        db = bitmap;
                    }
                    loader.cacheBitmap(memoryCacheKey, db);
                }
                setBitmap(imageView,db);
            }else{
                setDrawable(imageView, drawable);
            }
        }else{
            setBitmap(imageView, db);
        }
    }

    private void setBitmap(final ImageView view,final Bitmap bm) {
        //allow bitmap is null
        if (view == null) {
            return;
        }

        final Bitmap bitmap;
         if(bm != null && bm.isRecycled()){
            bitmap = null;
        }else{
            bitmap = bm;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                if(!isCancled()){
                    view.setImageBitmap(bitmap);
                }
            }
        });
    }

    private void setDrawable(final ImageView view, final Drawable dr) {
        if (dr == null || view == null) {
            return;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                log("Succeed SetResouce:" + view.hashCode() + ":drawable:" + dr);
                if (!isCancled())
                    view.setImageDrawable(dr);
            }
        });
    }


    private AtomicBoolean bCancled = new AtomicBoolean(false);

    public boolean isCancled() {
        return bCancled.get();
    }

    private boolean waitIfPaused() {
        return false;
    }

    private boolean delayIfNeed() {
        return checkTaskIsNotActual();
    }

    private boolean checkTaskIsNotActual() {
        String currentCacheKey = loader.getLoadingKeyForView(imageView);
        boolean imageViewWasReused = !memoryCacheKey.equals(currentCacheKey);
        if (imageViewWasReused) {
            log("Canceled");
        }
        return imageViewWasReused;
    }

    public void cancle() {
        this.bCancled.set(true);
    }

    private static void log(String message) {
        if (CacheUtil.DBG)
            Log.i("LoaderTask", message);
    }
}
