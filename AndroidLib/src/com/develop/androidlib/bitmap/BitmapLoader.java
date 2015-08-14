package com.develop.androidlib.bitmap;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.develop.androidlib.util.ArrayMap;

public class BitmapLoader {
	public static final String TAG = BitmapLoader.class.getSimpleName();
	private static ExecutorService SINGLE_TASK_EXECUTOR;

	static {
		SINGLE_TASK_EXECUTOR = (ExecutorService) Executors
				.newSingleThreadExecutor();
	}

	private static Bitmap sDefaultBitmap;

	public static enum TaskType {
		INSTALLED_APK, UNINSTLLED_APK, PHOTO_GALLARY, GUIDE_CUSTOM, GCM_ICON
	}

	public void recycle() {
		mImageCache.clear();
	}

	public static class Configuration {
		private int poolSize;
		private int cacheSize;
	}

	interface ImageLoadingListener {

		void onLoadingStarted(String imageUri, View view);

		void onLoadingFailed(String imageUri, View view, String failReason);

		void onLoadingComplete(String imageUri, View view, Bitmap loadedImage);

		void onLoadingCancelled(String imageUri, View view);
	}

	private static Configuration sDefaultConfig = new Configuration();

	static {
		sDefaultConfig.poolSize = 1;
		// BEGIN, James_Chan,
		// sDefaultConfig.cacheSize = 50;
		sDefaultConfig.cacheSize = (int) (Runtime.getRuntime().maxMemory() / 10);
		// END, James_Chan,
	}

	private ExecutorService mExecutor = Executors.newSingleThreadExecutor();
	private MemoryCache<String, Bitmap> mImageCache;

	Context mContext;

	private static BitmapLoader sInstance;

	private BitmapLoader(Configuration config, Context context) {
		mExecutor = Executors.newFixedThreadPool(config.poolSize);
		mImageCache = new BitmapCache(config.cacheSize);
		mContext = context.getApplicationContext();
		getDefaultBitmap();
	}

	public Bitmap getDefaultBitmap() {
		if (sDefaultBitmap == null || sDefaultBitmap.isRecycled()) {
			sDefaultBitmap = BitmapFactory.decodeResource(
					mContext.getResources(),
					android.R.drawable.sym_def_app_icon);
		}
		return sDefaultBitmap;
	}

	public synchronized static BitmapLoader getInstance(Context context) {
		if (sInstance == null) {
			sInstance = new BitmapLoader(sDefaultConfig, context);
		}
		return sInstance;
	}

	/**
	 * 最重要的方法
	 * 
	 * @param view
	 * @param key
	 */
	public void loadDrawable(ImageView view, String key, TaskType type,
			Object objTag) {
		assert (!TextUtils.isEmpty(key));

		// 还是解决许多刷新的问题
		if (Looper.myLooper().getThread() != Thread.currentThread()) {
			throw new RuntimeException("不可以在UI线程外调用loadDrawable方法");
		}
		view.setScaleType(ScaleType.FIT_CENTER);

		Bitmap bmp = getBitmapFromCache(key);
		// BEGIN, James_Chan
		if (bmp != null && !bmp.isRecycled()) {
			view.setImageBitmap(bmp);
			return;
		} else if (TextUtils.isEmpty(key)) {
			view.setImageBitmap(getDefaultBitmap());
			return;
		}
		// END, James_Chan

		view.setImageBitmap(null);
		String memoryCacheKey = CacheUtil.generateKey(key,
				CacheUtil.ImageSize.getDefault());
		prepareDisplayTaskFor(view, memoryCacheKey);
		LoadBitmapTask task = new LoadBitmapTask(mContext, view, sInstance,
				key, memoryCacheKey, type, objTag);
		submit(task);
	}

	public void loadDrawable(ImageView view, ActivityInfo info) {
		// 还是解决许多刷新的问题
		if (Looper.myLooper().getThread() != Thread.currentThread()) {
			throw new RuntimeException("不可以在UI线程外调用loadDrawable方法");
		}
		if (view == null)
			return;
		if (info == null) {
			view.setImageBitmap(getDefaultBitmap());
			return;
		}

		String salt = info.name + info.packageName;
		// TODO 需要完善的地方,就是图片的尺寸目前都用了默认的,如果需要用不同的尺寸,相关代码还要修改
		String generateKey = CacheUtil.generateKey(salt,
				CacheUtil.ImageSize.getDefault());
		Bitmap bmp = getBitmapFromCache(generateKey);
		if (bmp != null && !bmp.isRecycled()) {
			view.setImageBitmap(bmp);
			return;
		} else if (TextUtils.isEmpty(salt)) {
			view.setImageBitmap(getDefaultBitmap());
			return;
		} else {
			view.setImageBitmap(null);
			LoadIconTask task = new LoadIconTask(view, sInstance, info,
					generateKey);
			submit(task);
		}
	}

	public void loadDrawable(ImageView view, String key, TaskType type) {
		loadDrawable(view, key, type, null);
	}

	void submit(final Runnable task) {
		mExecutor.execute(new Runnable() {
			@Override
			public void run() {
				task.run();
			}
		});
	}

	private Object mLockObject = new Object();
	private final ArrayMap<Integer, String> cacheKeysForImageViews = new ArrayMap<Integer, String>();

	String getLoadingKeyForView(ImageView imageView) {
		synchronized (mLockObject) {
			return cacheKeysForImageViews.get(imageView.hashCode());
		}
	}

	public void prepareDisplayTaskFor(ImageView imageView, String memoryCacheKey) {
		synchronized (mLockObject) {
			cacheKeysForImageViews.put(imageView.hashCode(), memoryCacheKey);
		}
	}

	void cancelDisplayTaskFor(ImageView imageView) {
		synchronized (mLockObject) {
			cacheKeysForImageViews.remove(imageView.hashCode());
		}
	}

	/**
	 * 同步拿到Icon,失败返回默认图标
	 * 
	 * @param info
	 * @return
	 */
	public Bitmap loadIconSync(ApplicationInfo info) {
		if (info == null || TextUtils.isEmpty(info.packageName)) {
			return getDefaultBitmap();
		}
		Bitmap result = getBitmapFromCache(info.packageName);
		return (result != null) ? result : loadIcon(mContext, info);
	}

	public Bitmap loadIconSyncByPkgName(String pkgName) {
		if (TextUtils.isEmpty(pkgName)) {
			return getDefaultBitmap();
		}
		Bitmap result = getBitmapFromCache(pkgName);
		if (result != null) {
			return result;
		}
		try {
			Drawable d = mContext.getPackageManager().getApplicationIcon(
					pkgName);
			result = ((BitmapDrawable) d).getBitmap();
			if (result != null && !result.isRecycled()) {
				mImageCache.put(pkgName, result);
				return result;
			}
			d = null;
		} catch (OutOfMemoryError e) {
			e.printStackTrace();
			System.gc();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return getDefaultBitmap();
	}

	public Bitmap getBitmapFromCache(String key) {
		if (TextUtils.isEmpty(key)) {
			return null;
		}
		Bitmap b = mImageCache.get(key);
		if (b != null && b.isRecycled()) {
			mImageCache.remove(key);
			b = null;
		}
		return b;
	}

	public boolean getBitmapWithAsyncTask(String pkgName, View convertView,
			ImageView iconView) {
		Bitmap b = getBitmapFromCache(pkgName);
		iconView.setImageBitmap(b);

		if (b != null) {
			return true;
		} else {
			return false;
		}
	}

	public void cacheBitmap(String key, Bitmap bitmap) {
		mImageCache.put(key, bitmap);
	}

	private Bitmap loadIcon(Context context, ApplicationInfo info) {
		try {
			Drawable d = info.loadIcon(context.getPackageManager());
			Bitmap bm = ((BitmapDrawable) d).getBitmap();
			if (bm != null && !bm.isRecycled()) {
				mImageCache.put(info.packageName, bm);
				return bm;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return getDefaultBitmap();
	}
}
