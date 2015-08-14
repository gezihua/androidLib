package com.develop.androidlib.bitmap;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

import com.develop.androidlib.R;

public class LoadBitmapTask implements Runnable {

	ImageView imageView;
	BitmapLoader loader;
	String keyStr;
	String memoryCacheKey;
	Object obj;
	BitmapLoader.TaskType type;
	private Context mContext;

	static Handler handler = new Handler();

	private static int RES_DEFAULT_ICON = R.drawable.locker_broken_file_icon;

	public LoadBitmapTask(Context context, ImageView imageView,
			BitmapLoader loader, String keyStr, String memoryKey,
			BitmapLoader.TaskType type, Object o) {
		super();
		this.mContext = context;
		this.imageView = imageView;
		this.loader = loader;
		this.keyStr = keyStr;
		this.obj = o;// TODO 扩展
		this.memoryCacheKey = memoryKey;
		this.type = type;
	}

	private Bitmap GetSuitableBitmapForMiUI(Bitmap bmp) {
		if (bmp.isRecycled()) {
			return bmp;
		}
		if (bmp.getWidth() == 136 && bmp.getHeight() == 136) {
			Bitmap bmpSuit = Bitmap.createBitmap(bmp, 7, 7, 136 - 7 * 2,
					136 - 7 * 2);
			return bmpSuit;
		} else {
			return bmp;
		}
	}

	@Override
	public void run() {
		if (waitIfPaused())
			return;
		if (delayIfNeed())
			return;
		if (isCancled())
			return;
		if (TextUtils.isEmpty(keyStr)) {
			setBitmap(imageView, BitmapLoader.getInstance(mContext)
					.getDefaultBitmap());
		}
		Bitmap db = loader.getBitmapFromCache(keyStr);
		if (db == null) {
			switch (type) {
			case UNINSTLLED_APK:
				db = loadIconOfUninstalledApk(loader.mContext, keyStr);
				break;
			case INSTALLED_APK:
				db = loadIconOfInstalledPackage(loader.mContext, keyStr);
				break;
			default:
				db = null;
				break;
			}
			if (db != null && !db.isRecycled()
					&& BitmapLoader.TaskType.GCM_ICON != type) {
				loader.cacheBitmap(keyStr, db);
			}
		}

		if (db != null && !db.isRecycled()) {
			setBitmap(imageView, db);
			return;
		}

		setResouce(imageView, RES_DEFAULT_ICON);
	}

	private void setBitmap(final ImageView view, final Bitmap bm) {
		if (bm == null || view == null) {
			return;
		}
		handler.post(new Runnable() {
			@Override
			public void run() {
				log("Succeed SetBitmap:" + view.hashCode() + ":bitmap:"
						+ bm.hashCode());
				view.setImageBitmap(bm);
			}
		});
	}

	private void setResouce(final ImageView view, final int resId) {
		if (resId == 0 || view == null) {
			return;
		}
		handler.post(new Runnable() {
			@Override
			public void run() {
				log("Succeed SetResouce:" + view.hashCode() + ":resId:" + resId);
				if (!isCancled())
					view.setImageResource(resId);
			}
		});
	}

	/**
	 * return null if failed
	 * 
	 * @param context
	 * @param pkgName
	 * @return
	 */
	private Bitmap loadIconOfInstalledPackage(Context context, String pkgName) {
		Bitmap bitmap = null;

		try {
			bitmap = BitmapLoader.getInstance(mContext).loadIconSyncByPkgName(
					pkgName);
		} catch (Exception e) {
			e.printStackTrace();
			bitmap = null;
		} catch (OutOfMemoryError e) {
			long nSize = -1;
			try {
				ApplicationInfo info = context.getPackageManager()
						.getApplicationInfo(pkgName, 0);
				nSize = new File(info.publicSourceDir).length();
			} catch (Throwable e1) {
			}

			throw new OutOfMemoryError("Load Installed Icon: " + pkgName
					+ " - size:" + nSize);
		}
		return bitmap;
	}

	/**
	 * return null if failed
	 * 
	 * @param context
	 * @param filePath
	 * @return
	 */
	private Bitmap loadIconOfUninstalledApk(Context context, String filePath) {
		Bitmap icon = null;
		log("start:Uninstalled:" + filePath);
		try {
			if (filePath.endsWith(".apk")) {
				PackageInfo packageInfo = context.getPackageManager()
						.getPackageArchiveInfo(filePath,
								PackageManager.GET_ACTIVITIES);
				if (packageInfo != null) {
					ApplicationInfo appInfo = packageInfo.applicationInfo;
					if (Build.VERSION.SDK_INT >= 8) {
						appInfo.sourceDir = filePath;
						appInfo.publicSourceDir = filePath;
					}
					Drawable _db = appInfo
							.loadIcon(context.getPackageManager());
					icon = ((BitmapDrawable) _db).getBitmap();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		log("end:Uninstalled:" + (icon == null ? "failed" : "success")
				+ filePath);

		return icon;
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
