package com.develop.androidlib.bitmap;


import android.graphics.Bitmap;
import android.support.v4.util.LruCache;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;

/**
 * 新的Drawable 问题
 * @date 2013-9-6
 */
public class BitmapCache implements MemoryCache<String, Bitmap> {
	public static final String TAG = BitmapCache.class.getSimpleName();
	private LruCache<String, Bitmap> mImageCache;
	
	public BitmapCache(int maxSize) {
		mImageCache = new LruCache<String, Bitmap>(maxSize){
			@Override
			protected int sizeOf(String key, Bitmap value) {	
				return (value != null) ? (value.getRowBytes() * value.getHeight()) : 0;
			}
			
		};
	}

	
	@Override
	public synchronized boolean put(String key, Bitmap value) {
		mImageCache.put(key, value);
		return true;
	}

	@Override
	public synchronized Bitmap get(String key) {
		Bitmap value = mImageCache.get(key);
		return (value != null && !value.isRecycled()) ? value : null;
	}

	@Override
	public synchronized void remove(String key) {
		Bitmap value = mImageCache.remove(key);
		if (value != null && value.isRecycled() == false) {
			value.recycle();
			value = null;
		}
	}

	@Override
	public Collection<String> keys() {
		return mImageCache.snapshot().keySet();
	}


	@Override
	public void clear() {
		mImageCache.evictAll();
	}
	
	protected Reference<Bitmap> createReference(Bitmap value) {
		return new WeakReference<Bitmap>(value);
	}

	public synchronized final Map<String, Bitmap> snapshot() {
		return mImageCache.snapshot();
	}
	
	public int maxSize(){
		return mImageCache.maxSize();
	}

	@Override
	public String toString() {
		return (mImageCache != null) ? mImageCache.toString() : "Cache is empty";
	}
	
	
}
