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

package com.kazus.android.slidebento.ui.list;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.kazus.android.slidebento.io.AlbumDataManager;
import com.kazus.android.slidebento.util.BitmapHelper;
import com.kazus.android.slidebento.util.ImageCache;
import com.kazus.android.slidebento.R;

public class AlbumListItemAdapter extends ArrayAdapter<AlbumListItem> {
	private static final String TAG = "AlbumListItemAdapter";
	private static final int MAX_IMG_WIDTH = 160;
	private static final int MAX_IMG_HEIGHT = 160;
	private static final int DUMMY_IMG_WIDTH = 160;
	private static final int DUMMY_IMG_HEIGHT = 120;

	private AlbumDataManager mManager = AlbumDataManager.getInstance();
	private LayoutInflater mInflater;
	private ListView mListView = null;
	private Context mContext = null;

	public AlbumListItemAdapter(Context context, int resourceId,
			ListView listView) {
		super(context, resourceId);
		mContext = context;
		mInflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mListView = listView;
		Log.d(TAG, "AlbumListItemAdapter mListView=" + mListView);
	}

	@Override
	public int getCount() {
		int count = mManager.getListCount();
		return count;
	}

	@Override
	public AlbumListItem getItem(int position) {
		return mManager.getItem(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final ViewHolder holder;
		if (convertView == null) {
			// Create view from Layout File
			convertView = mInflater.inflate(R.layout.album_list_item, null);
			holder = new ViewHolder();
			holder.title = (TextView) convertView.findViewById(R.id.album_title);
			holder.playing = (TextView) convertView.findViewById(R.id.album_playing);
			holder.description = (TextView) convertView.findViewById(R.id.album_description);
			holder.imageView = (ImageView) convertView.findViewById(R.id.album_top_image);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}

		// Fetch item
		final AlbumListItem item = (AlbumListItem) getItem(position);

		// Set Title
		holder.title.setText(item.title);

		// Set Playing
		if(mManager.getMusubi()!=null && mManager.isPlaying() && mManager.getCurrentAlbumId().equals(mManager.getAlbumId(position))){
			holder.playing.setText(R.string.list_playing);
			holder.playing.setTextColor(convertView.getResources().getColor(
						R.color.body_playing));
			holder.title.setTextColor(convertView.getResources().getColor(
					R.color.body_playing));
		}else{
			holder.playing.setText("");
			holder.title.setTextColor(convertView.getResources().getColor(
					R.color.body_text_1));
		}
		
		
		// Set Description
		holder.description.setText(item.description);
		holder.description.setVisibility(View.VISIBLE);
		holder.description.setTextColor(
						convertView.getResources().getColor(R.color.body_text_2));

		// Set Image
		try {
			holder.imageView.setTag("Tag"+position);
			Bitmap bitmap = ImageCache.getImage(String.valueOf(position));
			if (bitmap == null) {
				holder.imageView.setImageBitmap(
						BitmapHelper.getDummyBitmap(DUMMY_IMG_WIDTH, DUMMY_IMG_HEIGHT));
				ImageGetTask task = new ImageGetTask(holder.imageView);
				task.execute(String.valueOf(position));
			} else {
				holder.imageView.setImageBitmap(bitmap);
				holder.imageView.setVisibility(View.VISIBLE);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return convertView;
	}

	static class ViewHolder {
		TextView playing;
		TextView title;
		TextView description;
		ImageView imageView;
	}

	class ImageGetTask extends AsyncTask<String, Void, Bitmap> {
		private ImageView image;
		private String tag;

		public ImageGetTask(ImageView imageView) {
			image = imageView;
			tag = image.getTag().toString();
		}

		@Override
		protected Bitmap doInBackground(String... params) {
			synchronized (mContext) {
				Log.d(TAG, "bgexec: position="+params[0]);
				try {
					Bitmap bitmap = mManager.getThumbBitmap(Integer.parseInt(params[0]),
							MAX_IMG_WIDTH, MAX_IMG_HEIGHT, 0);
					ImageCache.setImage(params[0], bitmap);
					return bitmap;
				} catch (Exception e) {
					return null;
				}
			}
		}

		@Override
		protected void onPostExecute(Bitmap result) {
//			Log.d(TAG, "tags: "+tag+", "+image.getTag()+";");
			if (tag.equals(image.getTag())) {
				if (result != null) {
					image.setImageBitmap(result);
				} else {
					image.setImageBitmap(BitmapHelper.getDummyBitmap(
							DUMMY_IMG_WIDTH, DUMMY_IMG_HEIGHT));
				}
				image.setVisibility(View.VISIBLE);
			}else{
				Log.d(TAG, "tag maching failed???");
			}
		}
	}
}
