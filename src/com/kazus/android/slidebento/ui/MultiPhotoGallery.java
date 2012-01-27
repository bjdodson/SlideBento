/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * ChangeLog 
 *   2012-01-06 Kazuya Yokoyama <kazuya.yokoyama@gmail.com>
 *   - Modified package name
 *   - Added single image view
 */

package com.kazus.android.slidebento.ui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import mobisocial.socialkit.musubi.DbObj;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;

import com.kazus.android.slidebento.io.AlbumDataManager;
import com.kazus.android.slidebento.util.BitmapHelper;
import com.kazus.android.slidebento.util.CorralClient;
import com.kazus.android.slidebento.util.UIUtils;
import com.kazus.android.slidebento.R;

import edu.stanford.junction.Junction;
import edu.stanford.junction.JunctionException;
import edu.stanford.junction.api.activity.JunctionActor;
import edu.stanford.junction.api.messaging.MessageHeader;

public class MultiPhotoGallery extends Activity implements AdapterView.OnItemSelectedListener {
	private static final String TAG = "PhotoGallery";
	
	private AlbumDataManager mManager = AlbumDataManager.getInstance();

	private ImageGalleryAdapter mAdapter1 = null;
	private ImageAdapter mAdapter2 = null;
//	private ImageView mSingleImage = null;
	
	private Gallery gallery1;
	private Gallery gallery2;

	private Context mContext;
	private CorralClient mCorralClient;
	private Bitmap bitmap;
	private int prev = 0;
	private Junction mJunction;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.multi_photo_gallery);
        mContext = this;
        mCorralClient = CorralClient.getInstance(this);

        // hide actionbar
        if(UIUtils.isTablet(this)){
        	if(getActionBar()!=null){
        		getActionBar().hide();
        	}
        }

        // Reference the Gallery view
        gallery1 = (Gallery) findViewById(R.id.gallery1);
        gallery2 = (Gallery) findViewById(R.id.gallery2);
        
        // Set the adapter to our custom adapter (below)
        mAdapter1 = new ImageGalleryAdapter(this);
        gallery1.setAdapter(mAdapter1);
        gallery1.setOnItemSelectedListener(this);

        mAdapter2 = new ImageAdapter(this);
        gallery2.setAdapter(mAdapter2);
        gallery2.setOnItemSelectedListener(this);

        if(mManager.getMusubi()!=null){
	        int position = mManager.getCurrentPlaySlide();
	        gallery1.setSelection(position);
	        gallery2.setSelection(position);
	        setCorral(position);
	        
	    	try {
	    		mJunction = mManager.getMusubi().junctionForObj(mStatusUpdater, mManager.getMusubi().getObj());
	    		pushUpdate(position);
	    	} catch (JunctionException e) {
	    		Log.e(TAG, "Junction Error");
	    	}
        }
    }
    
	@Override
	public void onDestroy(){
		mManager.closePort(this);
		super.onDestroy();
	}
    
	private JunctionActor mStatusUpdater = new JunctionActor() {
        @Override
        public void onMessageReceived(MessageHeader header, final JSONObject json) {
        	Log.d(TAG, "Msg Rcvd"+json.toString());
        	try {
        		if(json.has("updateReq")){
					int req = json.getInt("updateReq");
					if(req==1){
						pushUpdate(mManager.getCurrentPlaySlide());
					}
        		}
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
    };

    public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
    	if(gallery1.getSelectedItemPosition()!=gallery2.getSelectedItemPosition()){
    		if(gallery1.getSelectedItemPosition()!=position){
    			gallery1.setSelection(position, true);
    		}
    		if(gallery2.getSelectedItemPosition()!=position){
    			gallery2.setSelection(position, true);
    		}
    	}
    	if(mManager.getMusubi()!=null){
	        if(mManager.getCurrentPlaySlide()!=position){
	        	Log.d(TAG, "----Update---");
	        	mManager.updateSlidePosition(position);
	        	pushUpdate(position);
	        }
	    	bitmap = null;
	        setCorral(position);
    	}
    }
    
    private void pushUpdate(int position){
		JSONObject jso = new JSONObject();
		try {
			jso.put("position", position);
			jso.put("wanip", mManager.getWanIpAddress());
			jso.put("wanport", mManager.getWanPort());
			jso.put("lanip", AlbumDataManager.getLocalIpAddress());
		} catch (JSONException e) {
			Log.e(TAG, "Junction msg creation failed.");
		}
		if(mJunction!=null){
			mJunction.sendMessageToSession(jso);
		}
    }

    public void onNothingSelected(AdapterView<?> parent) {
    }

    public class ImageAdapter extends BaseAdapter {
        private Context mContext;
        private int mGalleryItemBackground;
    	private List<Bitmap> mImageList;
        
        public ImageAdapter(Context c) {
            mContext = c;
            // See res/values/attrs.xml for the <declare-styleable> that defines
            // PhotoGallery.
            TypedArray a = obtainStyledAttributes(R.styleable.PhotoGallery);
            mGalleryItemBackground = a.getResourceId(
                    R.styleable.PhotoGallery_android_galleryItemBackground, 0);
            a.recycle();

            mImageList = new ArrayList<Bitmap>();
            loadImages();
        }

        public int getCount() {
        	return mImageList.size();
//            return mImageIds.length;
        }

        public Bitmap getItem(int position) {
            return mImageList.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView i = new ImageView(mContext);

            i.setImageBitmap(mImageList.get(position));
//            i.setImageResource(mImageIds[position]);
            i.setScaleType(ImageView.ScaleType.FIT_XY);
            i.setLayoutParams(new Gallery.LayoutParams(136, 88));
            
            // The preferred Gallery item background
            i.setBackgroundResource(mGalleryItemBackground);
            
            return i;
        }

        private void loadImages() {
        	if(mManager.getMusubi()!=null){
	        	for (int i=0; i<mManager.getItemCount(); i++) {
	        		mImageList.add(mManager.objToBitmap(mManager.getObjByKey(i)));
	        	}
        	}else{
        		Uri[] urilist = mManager.getFilelistByUri(mManager.getLocalPlaying(),false);
	        	DisplayMetrics metrics = new DisplayMetrics();
	        	getWindowManager().getDefaultDisplay().getMetrics(metrics);
	        	for (int i=0; i<urilist.length; i++) {
                    float rotation = SinglePhotoGallery.rotationForImage(mContext, urilist[i]);
	        		mImageList.add(BitmapHelper.getResizedBitmap(new File(urilist[i].getPath()), 50, 50, rotation));
	        	}
        	}
       }
    }

    public class ImageGalleryAdapter extends BaseAdapter {
        private Context mContext;
    	private List<Bitmap> mImageList;
        
        public ImageGalleryAdapter(Context c) {
            mContext = c;

            mImageList = new ArrayList<Bitmap>();
            loadImages();
        }

        public int getCount() {
        	return mImageList.size();
//            return mImageIds.length;
        }

        public Bitmap getItem(int position) {
            return mImageList.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView im = new ImageView(mContext);
            
            im.setLayoutParams(new Gallery.LayoutParams(
                    Gallery.LayoutParams.MATCH_PARENT,
                    Gallery.LayoutParams.MATCH_PARENT));
            im.setScaleType(ImageView.ScaleType.FIT_CENTER);
            im.setBackgroundColor(Color.BLACK);
//            im.setImageResource(mImageIds[position]);
            im.setImageBitmap(mImageList.get(position));
            
            return im;
        }
        
        public void swapImg(int position, Bitmap bitmap){
        	mImageList.set(position, bitmap);
        	notifyDataSetChanged();
        }

        private void loadImages() {
        	if(mManager.getMusubi()!=null){
	        	for (int i=0; i<mManager.getItemCount(); i++) {
	        		mImageList.add(mManager.objToBitmap(mManager.getObjByKey(i)));
	        	}
        	}else{
        		Uri[] urilist = mManager.getFilelistByUri(mManager.getLocalPlaying(),false);
	        	for (int i=0; i<urilist.length; i++) {
                    float rotation = SinglePhotoGallery.rotationForImage(mContext, urilist[i]);
	        		mImageList.add(BitmapHelper.getResizedBitmap(new File(urilist[i].getPath()), 600, 400, rotation));
	        	}
        	}
        }

    }

	private final void setCorral(final int position){
		final DbObj obj = mManager.getObjByKey(position);
        new Thread() {
            public void run() {
                try {
                    if (!mCorralClient.fileAvailableLocally(obj)) {
                        //toast("Trying to go HD...");
                    }
                    // Log.d(TAG, "Trying to go HD...");
                    final Uri fileUri = mCorralClient.fetchContent(obj, mManager.getMusubi(), mManager.getLanIpAddress());
                    if (fileUri == null) {
                        Log.d(TAG, "Failed to go HD");
                        return;
                    }
                    // Log.d(TAG, "Opening HD file " + fileUri);
                    int filesize = obj.getJson().optInt(AlbumDataManager.OBJ_FILESIZE);

                    InputStream is = getContentResolver().openInputStream(fileUri);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    if(filesize>0){
                    	options.inSampleSize = (int) Math.floor(filesize / 400000);
                    }else{
                        options.inSampleSize = 4;
                    }
                    Log.d(TAG, "filesize="+String.valueOf(filesize));
                    Log.d(TAG, "inSampleSize="+String.valueOf(options.inSampleSize));

                    Matrix matrix = new Matrix();
                    float rotation = SinglePhotoGallery.rotationForImage(mContext, fileUri);
                    if (rotation != 0f) {
                        matrix.preRotate(rotation);
                    }
                    bitmap = BitmapFactory.decodeStream(is, null, options);

                    int width = bitmap.getWidth();
                    int height = bitmap.getHeight();
                    bitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, width, height, matrix, true);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                        	mAdapter1.swapImg(prev, mAdapter2.getItem(prev)); // avoid out of memory
                        	prev = position;
                            mAdapter1.swapImg(position, bitmap);
                        }
                    });
                } catch (IOException e) {
                    // toast("Failed to go HD");
                    Log.e(TAG, "Failed to get hd content", e);
                    // continue
                }
            };
        }.start();

	}
    
}
