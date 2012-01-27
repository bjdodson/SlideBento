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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.Toast;

import com.kazus.android.slidebento.io.AlbumDataManager;
import com.kazus.android.slidebento.ui.list.AlbumListItemAdapter;
import com.kazus.android.slidebento.util.ImageCache;
import com.kazus.android.slidebento.R;

public class AlbumListFragment extends ListFragment {
    private static final String TAG = "AlbumListFragment";
    
	private static AlbumDataManager mManager = AlbumDataManager.getInstance();
	private AlbumListItemAdapter mListAdapter = null;
	private ListView mListView = null;
	private List<File> mSelectedItems = new ArrayList<File>();

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
    	
        View root = inflater.inflate(R.layout.fragment_albumlist, container, false);
        
        // ListView
		mListView = (ListView) root.findViewById(android.R.id.list);
        mListView.setFastScrollEnabled(true);
        
        registerForContextMenu(mListView);
        return root;
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
    	super.onActivityCreated(savedInstanceState);
    	
        // Create adapter
        mListAdapter = new AlbumListItemAdapter(
        		getActivity(), 
        		android.R.layout.simple_list_item_1,
        		mListView);
        setListAdapter(mListAdapter);
        
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        menu.setHeaderTitle(R.string.edit_dialog_title);
        final AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
        int position = adapterMenuInfo.position;
        
    	mSelectedItems = new ArrayList<File>();
        menu.add(position, 0, 0, "Add Slides");
        menu.add(position, 1, 1, "Delete Slides");
        menu.add(position, 2, 2, "Delete Deck");

    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = item.getGroupId();
        switch (item.getItemId()) {
        case 0:
    		File dir = Environment.getExternalStorageDirectory();
        	addSlides(position, dir);
            break;
        case 1:
        	delSlides(position);
            break;
        case 2:
        	delDeck(position);
            break;
        }
		return false;
    }
    
    @Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		if(mManager.getItemCount(position)<=0){
			Toast.makeText(getActivity(), R.string.warning_noslides, Toast.LENGTH_LONG).show();
			return;
		}

		if(mManager.getMusubi() == null){
			mManager.setLocalPlaying(position);
			Intent intent = new Intent(getActivity(), MultiPhotoGallery.class);
			intent.putExtra(HomeActivity.EXTRA_SB, HomeActivity.EXTRA_SB);
	        ((BaseActivity) getActivity()).openActivityOrFragment(intent);
			return;
		}
		
		if(mManager.isOwner() && !mManager.isPlaying()){
			new prepareForPlay().execute(position);
			return;
		}
		
		if(mManager.isOwner() && mManager.getAlbumId(position).equals(mManager.getCurrentAlbumId())){
			if(!mManager.isFinishedPreparation()){
				Toast.makeText(getActivity(), R.string.warning_now_preparing, Toast.LENGTH_LONG).show();
				return;
			}
			mManager.openPort(getActivity());
			// Intent
			Intent intent = new Intent(getActivity(), MultiPhotoGallery.class);
			intent.putExtra(HomeActivity.EXTRA_SB, HomeActivity.EXTRA_SB);
	        ((BaseActivity) getActivity()).openActivityOrFragment(intent);
		}else{
			Toast.makeText(getActivity(), R.string.warning_start, Toast.LENGTH_LONG).show();
		}
		
		
	}

	// ----------------------------------------------------------
	// task
	// ----------------------------------------------------------
	class prepareForPlay extends AsyncTask<Integer, Integer, Exception> implements OnCancelListener{
		ProgressDialog progress_;

		@Override
		protected void onPreExecute() {
			progress_ = new ProgressDialog(getActivity());
			progress_.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			progress_.setCancelable(true);
			progress_.setOnCancelListener(this);
			progress_.setMessage("Preparing Slides.");
			progress_.setIndeterminate(false);
			progress_.show();
			int orientation = getResources().getConfiguration().orientation;
			getActivity().setRequestedOrientation(orientation);
			
		}

		@Override
		protected Exception doInBackground(Integer... params) {
			int position = Integer.valueOf(params[0]);
			
			Log.d(TAG, "Triggered preparation for position "+position);
			mManager.openPort(getActivity());
			
			final CharSequence msg = getString(R.string.feed_msg_added, mManager.getAlbumName(position));
			StringBuilder html = new StringBuilder(msg);
			mManager.playAlbum(getActivity(), position, html.toString());

			return null;
		}

		@Override
		protected void onPostExecute(Exception result) {
			refresh();
			// Intent
			Intent intent = new Intent(getActivity(), MultiPhotoGallery.class);
			intent.putExtra(HomeActivity.EXTRA_SB, HomeActivity.EXTRA_SB);
	        ((BaseActivity) getActivity()).openActivityOrFragment(intent);
	        
			getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
			progress_.dismiss();
		}
		
		@Override
		protected void onCancelled(Exception result) {
			Log.d(TAG, "Play Cancelled");
			progress_.dismiss();
		}
		
		@Override
		public void onCancel(DialogInterface dialog) {
			Log.d(TAG, "Cancel Requested");
			this.cancel(true);
		}

	}

	public void refresh() {
		Handler handler = new Handler();
		handler.post(new Runnable(){
			public void run(){
				mListAdapter.notifyDataSetChanged();
				mListView.invalidateViews();
			}
		});
    }
	
	private void addSlides(final int position, final File dir){

		Uri[] slidelist = mManager.getFilelistByUri(dir.getAbsolutePath(),true);
		if(slidelist == null){
			return;
		}
		final String[] filelist = urisToFnames(slidelist);

		String[] filelist_mod = new String[filelist.length];
		boolean[] checked = new boolean[filelist.length];
		for(int i=0;i<filelist.length;i++){
			if(new File(dir, filelist[i]).isDirectory()){
				filelist_mod[i]=filelist[i]+"/";
				checked[i] = false;
			}else{
				filelist_mod[i]=filelist[i];
				checked[i] = false;
			}
		}
        new AlertDialog.Builder(getActivity())
		.setTitle(R.string.addslides_dialog_title)
		.setIcon(android.R.drawable.ic_dialog_info)
		.setMultiChoiceItems(filelist_mod, checked, new DialogInterface.OnMultiChoiceClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which, boolean isChecked) {
            	File selitem = new File(dir, filelist[which]);
            	if(selitem.isDirectory()){
            		addSlides(position, selitem);
            		dialog.cancel();
            	}else{
    				if(isChecked){
    					mSelectedItems.add(selitem);
    				}else{
    					if(mSelectedItems.contains(selitem)){
    						mSelectedItems.remove(selitem);
    					}
    				}
            	}
            }	
        })
		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				for (File item:mSelectedItems) {  
					if(item.isFile()){
						copySlideFile(item, position, "_"+new Date().getTime()+mManager.getExtension(item.getName()));
					}
				}
				ImageCache.clearCache();
				refresh();
			}
		})
		.setNegativeButton("Cancel", null)
        .show();
	}
	
	
	private void delSlides(int position){
		String dirname = mManager.getAlbumDirname(position);
		final File dir = new File(dirname);
		Uri[] slidelist = mManager.getFilelistByUri(dirname, false);
		if(slidelist == null){
			return;
		}

		final String[] slidenames = urisToFnames(slidelist);
		String[] listnames = new String[slidenames.length];
		boolean[] checked = new boolean[slidenames.length];
		for(int i=0;i<checked.length;i++){
			checked[i] = false;
			listnames[i]="Slide"+(i+1)+": "+slidenames[i];
		}
		
        new AlertDialog.Builder(getActivity())
		.setTitle(R.string.delslides_dialog_title)
		.setIcon(android.R.drawable.ic_dialog_info)
		.setMultiChoiceItems(listnames, checked, new DialogInterface.OnMultiChoiceClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which, boolean isChecked) {
				File selitem = new File(dir, slidenames[which]);
				if(isChecked){
					mSelectedItems.add(selitem);
				}else{
					if(mSelectedItems.contains(selitem)){
						mSelectedItems.remove(selitem);
					}
				}
            }
        })
		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				for (File item:mSelectedItems) {
					Log.d(TAG, "del:"+item.getAbsolutePath());
					if(item.isFile()){
						deleteFile(item);
					}
				}
				ImageCache.clearCache();
				refresh();
			}
		})
		.setNegativeButton("Cancel", null)
        .show();
	}

	private void delDeck(final int position){
        new AlertDialog.Builder(getActivity())
		.setTitle(R.string.deldeck_dialog_title)
		.setIcon(android.R.drawable.ic_dialog_info)
		.setMessage(R.string.deldeck_dialog_text)
		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				deleteFile(new File(mManager.getAlbumDirname(position)));
				ImageCache.clearCache();
				refresh();
			}
		})
		.setNegativeButton("Cancel", null)
		.show();
	}
	
	public static boolean deleteFile(File dirOrFile) {
		if (dirOrFile.isDirectory()) {
			String[] children = dirOrFile.list();
			for (int i=0; i<children.length; i++) {
				boolean success = deleteFile(new File(dirOrFile, children[i]));
				if (!success) {
					return false;
				}
			}
		}
		return dirOrFile.delete();
	}
	
	public static boolean copySlideFile(File src, int position, String fname){
		InputStream is;
		try {
			is = new FileInputStream(src);
			OutputStream os = new FileOutputStream(mManager.getAlbumDirname(position)+"/"+fname);
			byte buf[]=new byte[1024];
		    int len;
		    while((len=is.read(buf))>0)
		    os.write(buf,0,len);
		    os.close();
		    is.close();

		} catch (FileNotFoundException e) {
			Log.e(TAG, "File copy failed.");
			return false;
		} catch (IOException e) {
			Log.e(TAG, "File copy failed.");
			return false;
		}
		return false;
	}
	
	private String[] urisToFnames(Uri[] uris){
		String[] strs = new String[uris.length];
		for(int i=0;i<uris.length;i++){
			strs[i] = new File(uris[i].getPath()).getName();
		}
		return strs;
	}

}
