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

package com.kazus.android.slidebento.ui;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.FeedObserver;
import mobisocial.socialkit.musubi.Musubi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.kazus.android.slidebento.io.AlbumDataManager;
import com.kazus.android.slidebento.ui.list.AlbumListItem;
import com.kazus.android.slidebento.util.BitmapHelper;
import com.kazus.android.slidebento.util.ImageCache;
import com.kazus.android.slidebento.util.JpgFileHelper;
import com.kazus.android.slidebento.R;

public class HomeActivity extends BaseActivity {
	public static final String EXTRA_SB = "com.kazus.android.slidebento.extra.EXTRA_SB";

	private static final String TAG = "HomeActivity";

	private AlbumDataManager mManager = AlbumDataManager.getInstance();
	private AlbumListFragment mAlbumListFragment = null;
	private Musubi mMusubi = null;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_albumlist);
		getActivityHelper().setupActionBar(null, 0);
		getActivityHelper().setActionBarColor(getResources().getColor(R.color.actionbar_text));

		FragmentManager fm = getSupportFragmentManager();
		mAlbumListFragment = (AlbumListFragment) fm.findFragmentById(R.id.fragment_albumlist);

		File workdir = new File(Environment.getExternalStorageDirectory()+"/SlideBento/Manual");
		if(workdir.mkdirs()){
			addDefaultImgs(workdir);
		}
		setupAlbumList(workdir.getAbsolutePath());
		
		// Check if this activity launched from internal activity
		if (getIntent().hasExtra(EXTRA_SB)) {
			// nothing to do for Musubi
			return;
		}

		// Check if this activity launched from Home Screen
		if (!Musubi.isMusubiIntent(getIntent())) {
			boolean bInstalled = false;
			try {
				bInstalled = Musubi.isMusubiInstalled(getApplication());
			} catch (Exception e) {
				bInstalled = false;
			}
			if (!bInstalled) {
				goMarket();
			}
			mManager.init(null,this);
		} else {
			// create Musubi Instance
			Intent intent = getIntent();
			mMusubi = Musubi.getInstance(this, intent);
			mManager.init(mMusubi,this);

			// Launch Gallery if playing
			if(!mManager.isOwner() && mManager.isPlaying()){
				if(!mManager.isFinishedPreparation()){
					Toast.makeText(this, R.string.warning_now_preparing, Toast.LENGTH_LONG).show();
				}else{
					// Intent
					Intent intent2 = new Intent(this, SinglePhotoGallery.class);
					intent2.putExtra(HomeActivity.EXTRA_SB, HomeActivity.EXTRA_SB);
					((BaseActivity) this).openActivityOrFragment(intent2);
				}

			}
		}
	}

	@Override
	protected void onDestroy() {
		ImageCache.clearCache();
		if (mMusubi != null) {
			mMusubi.getFeed().removeStateObserver(mStateObserver);
		}
		// TODO mManager.fin();
		super.onDestroy();
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		//Log.d(TAG, "onPostCreate");
		super.onPostCreate(savedInstanceState);
		getActivityHelper().setupHomeActivity();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.home_menu_items, menu);

		super.onCreateOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.menu_add) {
			addDeck();
			return true;
		}else if (item.getItemId() == R.id.menu_preferences) {
			showPref();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private boolean[] setting = new boolean[1];
	private void showPref(){
		String[] listnames = new String[]{"UPnP router control"};
        boolean[] checked = new boolean[listnames.length];
        checked[0] = getSharedPreferences("main",0).getBoolean("UPnP", false);
        
		new AlertDialog.Builder(this)
		.setTitle(R.string.preferences_dialog_title)
		.setIcon(android.R.drawable.ic_dialog_info)
		.setMultiChoiceItems(listnames, checked, new DialogInterface.OnMultiChoiceClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int selected, boolean isChecked) {
				if(isChecked){
					setting[selected] = true;
				}else{
					setting[selected] = false;
				}
            }
        })
		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				getSharedPreferences("main",0).edit().putBoolean("UPnP", setting[0]).commit();
			}
		})
		.setNegativeButton("Cancel", null)
        .show();
	}
	
	public void addDeck() {
		// Show Add dialog
		LayoutInflater factory = LayoutInflater.from(this);
		final View inputView = factory.inflate(R.layout.dialog_add, null);

		// EditText
		final EditText editTextTitle = (EditText) inputView.findViewById(R.id.edittext_album_title);

		AlertDialog.Builder addTodoDialog = new AlertDialog.Builder(this)
				.setTitle(R.string.add_dialog_title)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setView(inputView)
				.setCancelable(true)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						// fetch new item
						String title = editTextTitle.getText()
								.toString();
						// check string length
						if (title.length() > 0) {
							title = title.replace(" ", "_");
							new File(Environment.getExternalStorageDirectory()+"/SlideBento/"+title).mkdirs();
							ImageCache.clearCache();
							mAlbumListFragment.refresh();
						}
					}
				})
				.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								// nothing to do (dismiss dialog)
							}
						});
		final AlertDialog dialog = addTodoDialog.create();
		editTextTitle.setOnFocusChangeListener(new OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					dialog.getWindow().setSoftInputMode(
									WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
				}
			}
		});
		dialog.show();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			try {
				// uncomment below if you want to get small image
				// Bitmap b = (Bitmap)data.getExtras().get("data");

				File tmpFile = JpgFileHelper.getTmpFile();
				if (tmpFile.exists() && tmpFile.length() > 0) {
					float degrees = 0;
					try {
						ExifInterface exif = new ExifInterface(
								tmpFile.getPath());
						switch (exif.getAttributeInt(
								ExifInterface.TAG_ORIENTATION,
								ExifInterface.ORIENTATION_NORMAL)) {
						case ExifInterface.ORIENTATION_ROTATE_90:
							degrees = 90;
							break;
						case ExifInterface.ORIENTATION_ROTATE_180:
							degrees = 180;
							break;
						case ExifInterface.ORIENTATION_ROTATE_270:
							degrees = 270;
							break;
						default:
							degrees = 0;
							break;
						}
						Log.e(TAG, exif
								.getAttribute(ExifInterface.TAG_ORIENTATION));
					} catch (IOException e) {
						e.printStackTrace();
					}

					Bitmap bitmap = BitmapHelper.getResizedBitmap(tmpFile,
							BitmapHelper.MAX_IMAGE_WIDTH,
							BitmapHelper.MAX_IMAGE_HEIGHT, degrees);

					AlbumListItem item = new AlbumListItem();
					item.title = "Title";
					item.description = "Description";
					item.image = bitmap;

					mAlbumListFragment.refresh();

					tmpFile.delete();
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	
	public void setupAlbumList(String workdir){
		mManager.getFilelistByUri(workdir, true);
		
	}

	private void addDefaultImgs(File workdir){
		try {
			int ids[] = {R.drawable.slide1,R.drawable.slide2,R.drawable.slide3,R.drawable.slide4,R.drawable.slide5
					    ,R.drawable.slide6,R.drawable.slide7,R.drawable.slide8,R.drawable.slide9};
			for(int i=0;i<ids.length;i++){
				String fpath  = workdir.getPath()+"/file"+(i+1)+".jpg";
				InputStream is = getResources().openRawResource(ids[i]);
				OutputStream os = new FileOutputStream(fpath);
				byte buf[]=new byte[1024];
			    int len;
			    while((len=is.read(buf))>0)
			    os.write(buf,0,len);
			    os.close();
			    is.close();
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	// ----------------------------------------------------------
	// Musubi
	// ----------------------------------------------------------
	private final FeedObserver mStateObserver = new FeedObserver() {
		@Override
		public void onUpdate(DbObj obj) {
			mManager.updateFromObj(obj);
		}
	};
	
	public void goMarket() {
		AlertDialog.Builder marketDialog = new AlertDialog.Builder(this)
				.setTitle(R.string.market_dialog_title)
				.setMessage(R.string.market_dialog_text)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setCancelable(true)
				.setPositiveButton(getResources().getString(R.string.market_dialog_yes), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						// Go to Android Market
						startActivity(Musubi.getMarketIntent());
						finish();
					}
				})
				.setNegativeButton(getResources().getString(R.string.market_dialog_no),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								finish();
							}
						});
		marketDialog.create().show();
	}
    
}