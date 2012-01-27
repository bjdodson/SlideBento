/*
 * Copyright (C) 2012 Kazuya Yokoyama <kazuya.yokoyama@gmail.com>
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
 */

package com.kazus.android.slidebento.io;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import mobisocial.socialkit.musubi.DbFeed;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.musubi.multiplayer.FeedRenderable;
import mobisocial.socialkit.obj.MemObj;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.util.Log;

import com.kazus.android.slidebento.ui.list.AlbumListItem;
import com.kazus.android.slidebento.util.BitmapHelper;
import com.kazus.android.slidebento.util.UpnpController;

public class AlbumDataManager {
	private static final String TAG = "AlbumDataManager";

	public static final String TYPE_APP_STATE = "appstate";
	public static final String TYPE_PICTURE   = "picture";

	public static final String OBJ_MIME_TYPE = "mimeType";
    public static final String OBJ_LOCAL_URI = "localUri";
    public static final String OBJ_HASHCODE = "hashCode";
    public static final String OBJ_FILESIZE = "fileSize";
	
	public static final String STATE_ALBUM = "albumState";
	public static final String STATE_TOTALSLIDES = "totalSlides";

	public static final String FIELD_CURRENT_INDEX = "currIndex";
	public static final String FIELD_ALBUM_ID = "albumId";
	public static final String FIELD_ALBUM_NAME = "albumName";

//    public static final String ATTR_LAN_IP = "vnd.mobisocial.device/lan_ip";
    public static final long MY_ID = -666;

    private static AlbumDataManager sInstance = null;
	private static JSONObject sAlbumState = null; // current selected album status
	private int mPlaySlide = 0;
	private int sTotalSlides = 0;
	private Musubi mMusubi = null;
	private String[] ftypes = {".png",".jpg",".jpeg",".gif"};

	private UpnpController upnp = null;
	private int upnpPort = 8224;
	private String wanip = null;
	private String wanport = null;
	private String lanip = null;
	
	private static int mLastState = 0;
	
	private static String localAlbumDir;

	// ----------------------------------------------------------
	// Instance
	// ----------------------------------------------------------
	private AlbumDataManager() {
		// nothing to do
	}

	public static AlbumDataManager getInstance() {
		if (sInstance == null) {
			sInstance = new AlbumDataManager();
		}

		return sInstance;
	}

	public void init(Musubi musubi, Context context) {
		mMusubi = musubi;
		sAlbumState = null;
		mPlaySlide = 0;
		mLastState = 0;
        wanip = null;
        wanport = null;
        lanip = null;
		if(musubi!=null){
			DbObj obj = getLatestAppstate();
			updateFromObj(obj);
		}
	}

	public void updateFromObj(DbObj obj){
		if (obj != null && obj.getJson() != null) {
			if (obj.getJson().has(STATE_ALBUM)){
				sAlbumState = obj.getJson().optJSONObject(STATE_ALBUM);
			}
			if (obj.getJson().has(STATE_TOTALSLIDES)){
				try {
					sTotalSlides = obj.getJson().getInt(STATE_TOTALSLIDES);
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public void fin() {
		if (sAlbumState != null) {
			sAlbumState = null;
		}
		if (sInstance != null) {
			sInstance = null;
		}
	}

	// ----------------------------------------------------------
	// Get / Retrieve
	// ----------------------------------------------------------
	public Musubi getMusubi() {
		return mMusubi;
	}

	synchronized public AlbumListItem getItem(int position) {
		// ignore position
		
		AlbumListItem item = new AlbumListItem();
		String albumdir = getAlbumDirname(position);
		File dir = new File(albumdir);
		
		if(!dir.exists()){
			return null;
		}
		item.title = dir.getName();
		item.description = DateFormat.getDateInstance().format(new Date(dir.lastModified()));
		
		Uri[] imgUris = getFilelistByUri(albumdir,false);
		if(imgUris!=null && imgUris.length>0){
			File imgfile = new File(imgUris[0].getPath());
			Bitmap resized = BitmapHelper.getResizedBitmap(imgfile, 160, 120, 0);
			if(resized!=null){
				item.image = resized;
			}else{
				item.image = BitmapHelper.getDummyBitmap(160, 120);
			}
		}else{
			item.image = BitmapHelper.getDummyBitmap(160, 120);
		}

		return item;
	}
	
	// For local play mode (not from Musubi Intent)
	synchronized public void setLocalPlaying(int position) {
		localAlbumDir = getAlbumDirname(position);
		return;
	}
	synchronized public String getLocalPlaying() {
		return localAlbumDir;
	}
	
    public String getWanIpAddress() {
    	return wanip;
    }
    public String getWanPort() {
    	return wanport;
    }
    public String getLanIpAddress() {
    	return lanip;
    }
    public void setLanIpAddress(String ip) {
    	lanip = ip;
    }
    public void setWanIpAddress(String ip) {
    	wanip = ip;
    }
    public void setWanPort(String port) {
    	wanport = port;
    }

	synchronized public String getAlbumDirname(int position) {
		
		String dirname = Environment.getExternalStorageDirectory()+"/SlideBento";
		String[] dirlist = getAlbumDirnameList(dirname);
		return dirlist[position];
		
	}
	
	synchronized public Bitmap getThumbBitmap(int position, int targetWidth,
			int targetHeight, float degrees) {
		
		Bitmap bitmap = getItem(position).image;
		// dummy
		if (bitmap == null) {
			bitmap = BitmapHelper.getDummyBitmap(targetWidth, targetHeight);
		} else {
			bitmap = BitmapHelper.getResizedBitmap(bitmap, targetWidth, targetHeight, degrees);
		}

		return bitmap;
	}

	synchronized public int getListCount() {
		String dirname = Environment.getExternalStorageDirectory()+"/SlideBento";
		File alistdir = new File(dirname);
		if(alistdir.listFiles()!=null){
			return alistdir.listFiles().length;
		}
		return 0;
	}

	private void initAlbumState(Context context, int position) throws JSONException{
		// TODO get imageUris from DB
//    	String uuid = UUID.randomUUID().toString();
		
		// TODO avoid multiple post
		String albumdir = getAlbumDirname(position);
		Log.d(TAG, "DIR:"+albumdir);
		Uri[] imgUris = getFilelistByUri(albumdir, false);
		Log.d(TAG, "imgUris:"+String.valueOf(imgUris));
		for(int i=0;i<imgUris.length;i++){
			pushSlideImage(context, imgUris[i]);
		}
		sTotalSlides = imgUris.length;
		
		JSONArray imgUrisJSON = new JSONArray();
		for(int i=0;i<imgUris.length;i++){
			imgUrisJSON.put(imgUris[i].toString());
		}
		
		sAlbumState = new JSONObject();
		sAlbumState.put(FIELD_ALBUM_ID,getAlbumId(position));
		sAlbumState.put(FIELD_ALBUM_NAME,getAlbumName(position));
	}
	
	// TODO temp
	public String getAlbumId(int position){
		return getAlbumDirname(position);
	}
		
	private JSONObject getStateObj() {
		JSONObject out = new JSONObject();
		try {
			out.put(STATE_ALBUM, getAlbumState());
			out.put(STATE_TOTALSLIDES, getItemCount());
		} catch (JSONException e) {
			Log.e(TAG, "Failed to put JSON", e);
		}
		return out;
	}
	synchronized public JSONObject getAlbumState() {
		return sAlbumState;
	}
	synchronized public int getItemCount(){
    	return sTotalSlides;
    }
	synchronized public int getItemCount(int position) {
		String[] list = new File(getAlbumDirname(position)).list();
		return list.length;
	}

	synchronized public String getCurrentAlbumId(){
		Log.d(TAG,"---ALBUMSTATE:"+String.valueOf(sAlbumState));
		if(sAlbumState!=null && sAlbumState.has(FIELD_ALBUM_ID)){
			try {
				return sAlbumState.getString(FIELD_ALBUM_ID);
			} catch (JSONException e) {
				return null;
			}
		}
    	return null;
    }
	synchronized public int getCurrentPlaySlide(){
		return mPlaySlide;
    }
	synchronized public void updateSlidePosition(int position){
		mPlaySlide = position;
    }
	synchronized public boolean isPlaying(){
		if(sAlbumState!=null){
			return true;
		}
		return false;
    }
	synchronized public boolean isOwner(){
		if(mMusubi.getObj().getSender().getLocalId() == MY_ID){
			return true;
		}
    	return false;
    }

	// sort by filename
	public Uri[] getFilelistByUri(String dirname, boolean showdir){
		final class Data {
			private File _data;
			public Data(File data) {
				_data = data;
			}
			public File	getFile() {
				return	_data;
			}
			public	int	Compare(Data cmp) {
				String	str1 = _data.getAbsolutePath();
				String	str2 = cmp._data.getAbsolutePath();
				if(cmp == null || cmp._data == null || _data == null)
					return	0;
				if(_data.isDirectory() == cmp._data.isDirectory())
					return	str1.compareToIgnoreCase(str2);
				if(_data.isDirectory())
					return	-1;
				return	1;
			}
		}
		final class DataComparator implements java.util.Comparator<Object> {
			public int compare(Object o1, Object o2) {
				return	((Data)o1).Compare((Data)o2);
			}
		}
		
		File file = new File(dirname);
		int	i;
		File[]	afTmp;
		afTmp = file.listFiles();
		if(afTmp == null || afTmp.length == 0)
			return	null;
		
		java.util.ArrayList<Data>	alist = new ArrayList<Data>();
		for(i = 0; i < afTmp.length; i++)
		{
			boolean listshow = false;
			if(afTmp[i].isHidden() == false){
				for(int j=0; j < ftypes.length;j++){
					if(ftypes[j].equalsIgnoreCase(this.getExtension(afTmp[i].getName()))){
						listshow = true;
					}
				}
				if(showdir && afTmp[i].isDirectory()){
					listshow = true;
				}
			}
			if(listshow){
				alist.add(new Data(afTmp[i]));
			}
		}
		Object[] aObject = alist.toArray();
		Arrays.sort(aObject,new DataComparator());

		Uri[] listuri = new Uri[aObject.length];
		for(i = 0; i < aObject.length; i++){
			listuri[i] = Uri.fromFile(((Data)aObject[i]).getFile());
		}
		return	listuri;
	}
	
	// sort by lastModified
	public String[] getAlbumDirnameList(String dirname){
		final class Data {
			private File _data;
			public Data(File data) {
				_data = data;
			}
			public File	getFile() {
				return	_data;
			}
			public	int	Compare(Data cmp) {
				long ts1 = _data.getAbsoluteFile().lastModified();
				long ts2 = cmp._data.getAbsoluteFile().lastModified();
				if(cmp == null || cmp._data == null || _data == null)
					return	0;
				if(ts1 > ts2)
					return -1;
				return	1;
			}
		}
		final class DataComparator implements java.util.Comparator<Object> {
			public int compare(Object o1, Object o2) {
				return	((Data)o1).Compare((Data)o2);
			}
		}
		
		File file = new File(dirname);
		int	i;
		File[]	afTmp;
		afTmp = file.listFiles();
		if(afTmp == null || afTmp.length == 0)
			return	null;
		
		java.util.ArrayList<Data>	alist = new ArrayList<Data>();
		for(i = 0; i < afTmp.length; i++)
		{
			if(afTmp[i].isHidden() == false)
				alist.add(new Data(afTmp[i]));
		}
		Object[] aObject = alist.toArray();
		Arrays.sort(aObject,new DataComparator());

		String[] listfname = new String[aObject.length];
		for(i = 0; i < aObject.length; i++){
			listfname[i] = ((Data)aObject[i]).getFile().getAbsolutePath();
		}
		return	listfname;
	}

	// ----------------------------------------------------------
	// Musubi
	// ----------------------------------------------------------
	
	public boolean isFinishedPreparation(){
		if(getLatestAppstate()!=null){
			return true;
		}
		return false;
	}
	
    public DbObj getLatestAppstate(){
		DbObj obj = null;
		if(mMusubi.getObj()!= null){
			DbFeed mFeed = mMusubi.getObj().getSubfeed();
			String sortOrder = "key_int desc";
			String selection = "type = 'appstate'";
			Cursor c = mFeed.query(null, selection, null, sortOrder);
			if(c != null && c.moveToFirst()){
				obj =  mMusubi.objForCursor(c);
			}
		}else{
			Log.e(TAG, "Cannot get App obj from feed!");
		}
		return obj;
    }

	public DbObj getObjByKey(int slideNo){
		DbObj obj = null;
		DbFeed mFeed = mMusubi.getObj().getSubfeed();
		String selection = "key_int = '"+slideNo+"'";
		String sortOrder = "timestamp asc";
		Cursor c = mFeed.query(null, selection, null, sortOrder);
		Log.d(TAG, "RESULT:"+c.getCount());
		if(c != null && c.moveToFirst()){
			obj =  mMusubi.objForCursor(c);
		}
		return obj;
	}

	// imprement
	synchronized public String getCurrentAlbumName(){
		if(sAlbumState!=null && sAlbumState.has(FIELD_ALBUM_NAME)){
			try {
				return sAlbumState.getString(FIELD_ALBUM_NAME);
			} catch (JSONException e) {
				return null;
			}
		}
    	return null;
    }
	public String getAlbumName(int position){
		return getItem(position).title;
	}
	
	public void playAlbum(Context context, int position, String htmlMsg){
        try {
        	initAlbumState(context, position);
        	pushUpdate(htmlMsg);
        } catch (JSONException e) {
			Log.e(TAG, "Failed to post JSON", e);
        }
	}
	
	public void pushUpdate(String htmlMsg){
		JSONObject b;
		try {
			b = new JSONObject(getStateObj().toString());
			// TODO add thumbnail
//			JSONObject diff = new JSONObject();
//			diff.put(DIFF_ADDED_UUID, addedUUID);
//			b.put(DIFF, diff);
//			b.put(B64JPGTHUMB, data);

			FeedRenderable renderable = FeedRenderable.fromHtml(htmlMsg);
			renderable.withJson(b);
			mMusubi.getObj().getSubfeed().postObj(new MemObj(TYPE_APP_STATE, b, null, mLastState));
			mLastState++;
//			mMusubi.getFeed().postObj(new MemObj(TYPE_APP_STATE, b));
		} catch (JSONException e) {
			Log.e(TAG, "Failed to post JSON", e);
		}
	}

	public void pushSlideImage(Context context, Uri imageUri) {
		try {
			Log.d(TAG, "pushSlideImage");
			
	        ContentResolver cr = context.getContentResolver();
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(cr.openInputStream(imageUri), null, options);
			
			// TODO use BitmapHelper Class
			int targetSize = 200;
			int xScale = (options.outWidth  + targetSize - 1) / targetSize;
			int yScale = (options.outHeight + targetSize - 1) / targetSize;
			int scale = xScale < yScale ? xScale : yScale;
			
			int size = 2000000;
			FileInputStream file = new FileInputStream(imageUri.getPath());
			try {
				size = file.available();
				file.close();
				Log.d(TAG, "Filesize:"+String.valueOf(size));
			} catch (IOException e1) {
				Log.d(TAG, "Failed to get filesize.");
			}
		
			options.inJustDecodeBounds = false;
			options.inSampleSize = scale;
			InputStream is = cr.openInputStream(imageUri);
			Bitmap sourceBitmap = BitmapFactory.decodeStream(is, null, options);
			
	        int width = sourceBitmap.getWidth();
	        int height = sourceBitmap.getHeight();
	        int cropSize = Math.min(width, height);

	        float scaleSize = ((float) targetSize) / cropSize;

	        Matrix matrix = new Matrix();
	        matrix.postScale(scaleSize, scaleSize);
	        float rotation = rotationForImage(context, imageUri);
	        if (rotation != 0f) {
	            matrix.preRotate(rotation);
	        }

	        Bitmap resizedBitmap = Bitmap.createBitmap(
	                sourceBitmap, 0, 0, width, height, matrix, true);

	        ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
	        byte[] data = baos.toByteArray();
	        sourceBitmap.recycle();
	        sourceBitmap = null;
	        resizedBitmap.recycle();
	        resizedBitmap = null;
	        System.gc(); // TODO: gross.

	        // TODO: Proper Content Corral API.
	        JSONObject base = new JSONObject();
	        try {
	            String type = cr.getType(imageUri);
	            if (type == null) {
	                type = "image/jpeg";
	            }
	            base.put(OBJ_LOCAL_URI, imageUri.toString());
	            base.put(OBJ_MIME_TYPE, type);
	            base.put(OBJ_FILESIZE, size);
//	            String localIp = getLocalIpAddress();
//	            if (localIp != null) {
//	                base.put(ATTR_LAN_IP, localIp);
//	            }
	        } catch (JSONException e) {
				Log.e(TAG, "Failed to post JSON", e);
	        }

			mMusubi.getObj().getSubfeed().postObj(new MemObj(TYPE_PICTURE, base, data, mLastState));
			mLastState++;
		} catch (FileNotFoundException e) {
			Log.e(TAG, "File not Found", e);
		}
	}


	// TODO use BitmapHelper Class
    static float rotationForImage(Context context, Uri uri) {
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
    static float exifOrientationToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }
    
	// ----------------------------------------------------------
	// Utility
	// ----------------------------------------------------------
    
    public Bitmap objToBitmap(DbObj obj) {
    	if(obj!=null){
    		byte[] bytes = obj.getRaw();
    		if(bytes!=null){
    			return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    		}
    	}
    	return BitmapHelper.getDummyBitmap(300, 200);
    }
    
    // from ContentCorral
    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
                    .hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
                        .hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        // not ready for IPv6, apparently.
                        if (!inetAddress.getHostAddress().contains(":")) {
                            return inetAddress.getHostAddress().toString();
                        }
                    }
                }
            }
        } catch (SocketException ex) {

        }
        return null;
    }

	public String getExtension(String fname){
        String filenameArray[] = fname.split("\\.");
        String extension = filenameArray[filenameArray.length-1];
        if(extension!=null){
        	return "."+extension;
        }else{
        	return "";
        }
	}

	// ----------------------------------------------------------
	// UPnP
	// ----------------------------------------------------------
	public void openPort(Context context){
		if(context.getSharedPreferences("main",0).getBoolean("UPnP", false)){
			Log.e(TAG, "Trying to open port.");
	        wanip = null;
	        wanport = null;
			upnp=null;
			upnp = new MyUpnpController(context,upnpPort);
			upnp.startService();
		}
	}
	public void closePort(Context context){
        wanip = null;
        wanport = null;
		if(upnp!=null){
			upnp.closePort();
		}
	}
	private final class MyUpnpController extends UpnpController{
		public MyUpnpController(Context context, int port) {
			super(context, port);
		}
		
		@Override
		public void onGetExternalIP(String ipaddress){
            Log.e(TAG, "Suceed to set portmapping! ExternalIP:"+ipaddress+", LocalIP: "+getLocalIpAddress()+" Port:"+getPort());
            wanip = ipaddress;
            wanport = String.valueOf(getPort());
		}
		
		@Override
	    public void onPortClosed(){
			Log.e(TAG, "Suceed to remove portmapping!");
			upnp.stopService();
		}
	}
}
