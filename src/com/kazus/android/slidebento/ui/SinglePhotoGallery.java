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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;

import com.kazus.android.slidebento.io.AlbumDataManager;
import com.kazus.android.slidebento.util.CorralClient;
import com.kazus.android.slidebento.util.UIUtils;
import com.kazus.android.slidebento.R;

import edu.stanford.junction.Junction;
import edu.stanford.junction.JunctionException;
import edu.stanford.junction.api.activity.JunctionActor;
import edu.stanford.junction.api.messaging.MessageHeader;

public class SinglePhotoGallery extends Activity implements AdapterView.OnItemSelectedListener{
	private static final String TAG = "SinglePhotoGallery";
	
	private AlbumDataManager mManager = AlbumDataManager.getInstance();

	private Gallery gallery;
	private ImageGalleryAdapter mAdapter;
	private Context mContext;
	private CorralClient mCorralClient;
	private Bitmap bitmap;
	private Bitmap prevItem=null;
	private int prev=0;
	private Junction mJunction = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
		requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // hide actionbar
        if(UIUtils.isTablet(this)){
        	if(getActionBar()!=null){
        		getActionBar().hide();
        	}
        }
        
        setContentView(R.layout.single_photo_gallery);
        mCorralClient = CorralClient.getInstance(this);

        // Reference the Gallery view
        gallery = (Gallery) findViewById(R.id.gallery);
        
        // Set the adapter to our custom adapter (below)
        mAdapter = new ImageGalleryAdapter(this);
        gallery.setAdapter(mAdapter);
        gallery.setOnItemSelectedListener(this);

        gallery.setSelection(mManager.getCurrentPlaySlide(), true);
        setCorral(mManager.getCurrentPlaySlide());
        
        if(mManager.getMusubi()!=null){
//	        mManager.getMusubi().getObj().getSubfeed().registerStateObserver(mStateObserver);
        		        
	    	try {
	    		mJunction = mManager.getMusubi().junctionForObj(mStatusUpdater, mManager.getMusubi().getObj());
	    		JSONObject jso = new JSONObject();
	    		try {
	    			jso.put("updateReq", 1);
	    		} catch (JSONException e) {
	    			Log.e(TAG, "Junction msg creation failed.");
	    		}
	    		if(mJunction!=null){
	    			mJunction.sendMessageToSession(jso);
	    		}
	    	} catch (JunctionException e) {
	    		Log.e(TAG, "Junction Error");
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
        	for (int i=0; i<mManager.getItemCount(); i++) {
        		mImageList.add(mManager.objToBitmap(mManager.getObjByKey(i)));
        	}
        	prev=0;
        	prevItem=mImageList.get(prev);
        }

    }

	// ----------------------------------------------------------
	// Musubi
	// ----------------------------------------------------------
	
	private final void setCorral(final int position){
		final DbObj obj = mManager.getObjByKey(position);
        new Thread() {
            public void run() {
                try {
                    if (!mCorralClient.fileAvailableLocally(obj)) {
                        //toast("Trying to go HD...");
                    }
                    Log.d(TAG, "Trying to go HD...");
                    Log.d(TAG, "IP:"+String.valueOf(mManager.getWanIpAddress()));
                    Uri fileUri;
                    if(mManager.getWanIpAddress()!=null){
                    	fileUri = mCorralClient.fetchContent(obj, mManager.getMusubi(), mManager.getWanIpAddress());
                    	if (fileUri == null) {
                            Log.d(TAG, "IP:"+String.valueOf(mManager.getLanIpAddress()));
                        	fileUri = mCorralClient.fetchContent(obj, mManager.getMusubi(), mManager.getLanIpAddress());
                        	if (fileUri == null) {
	                    		Log.d(TAG, "Failed to go HD");
	                    		return;
                        	}
                    	}
                    }else{
                        Log.d(TAG, "IP:"+String.valueOf(mManager.getLanIpAddress()));
                    	fileUri = mCorralClient.fetchContent(obj, mManager.getMusubi(), mManager.getLanIpAddress());
                    }
                    if(fileUri == null){
                		Log.d(TAG, "Failed to go HD");
                    	return;
                    }
                    Log.d(TAG, "Opening HD file " + fileUri);
                    int filesize = obj.getJson().optInt(AlbumDataManager.OBJ_FILESIZE);

                    InputStream is = getContentResolver().openInputStream(fileUri);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    if(filesize>0){
                    	options.inSampleSize = (int) Math.floor(filesize / 307200);
                    }else{
                        options.inSampleSize = 4;
                    }
                    Log.d(TAG, "Filesize="+String.valueOf(filesize));
                    Log.d(TAG, "inSampleSize="+String.valueOf(options.inSampleSize));

                    Matrix matrix = new Matrix();
                    float rotation = SinglePhotoGallery.rotationForImage(mContext, fileUri);
                    if (rotation != 0f) {
                        matrix.preRotate(rotation);
                    }
                    bitmap = BitmapFactory.decodeStream(is, null, options);
                    
                    // remove broken file
                    if(bitmap == null){
                    	File brokenfile = new File(fileUri.getPath());
                    	if(brokenfile.exists()){
                    		brokenfile.delete();
                    	}
                    	return;
                    }

                    int width = bitmap.getWidth();
                    int height = bitmap.getHeight();
                    bitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, width, height, matrix, true);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                        	mAdapter.swapImg(prev, prevItem); // avoid out of memory
                        	prevItem = mAdapter.getItem(position);
                        	prev = position;
                        	mAdapter.swapImg(position, bitmap);
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

	public static float rotationForImage(Context context, Uri uri) {
	    if (uri.getScheme().equals("content")) {
            String[] projection = { Images.ImageColumns.ORIENTATION };
            Cursor c = context.getContentResolver().query(
                    uri, projection, null, null, null);
            try {
	            if (c.moveToFirst()) {
	                return c.getInt(0);
	            }
            } finally {
            	c.close();
            }
        } else if (uri.getScheme().equals("file")) {
            try {
                ExifInterface exif = new ExifInterface(uri.getPath());
                int rotation = (int) exifOrientationToDegrees(
                        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL));
                return rotation;
            } catch (IOException e) {
                Log.e(TAG, "Error checking exif", e);
            }
        }
	    return 0f;
	}
	
	private static float exifOrientationToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }
	
	private JunctionActor mStatusUpdater = new JunctionActor() {
        @Override
        public void onMessageReceived(MessageHeader header, final JSONObject json) {
        	Log.d(TAG, "Msg Rcvd"+json.toString());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
        			try {
	    				int position = json.getInt("position");
	    				mManager.setLanIpAddress(json.getString("lanip"));
	    				if(json.has("wanip") && json.has("wanport")){
		    				mManager.setWanIpAddress(json.getString("wanip"));
		    				mManager.setWanPort(json.getString("wanport"));
	    				}else{
		    				mManager.setWanIpAddress(null);
		    				mManager.setWanPort(null);
	    				}
	        	        gallery.setSelection(position, true);
	        	        setCorral(position);
	                	Log.d(TAG, "position:"+position);
        			} catch (JSONException e) {
        				Log.e(TAG,"Cannot read position from Junciton");
        			}
                }
            });

        }
    };

	@Override
	public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
    	bitmap = null;
        setCorral(position);
	}

	@Override
	public void onNothingSelected(AdapterView<?> arg0) {
		// TODO Auto-generated method stub
		
	}

}
