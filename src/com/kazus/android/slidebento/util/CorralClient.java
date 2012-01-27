package com.kazus.android.slidebento.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import mobisocial.socialkit.SignedObj;
import mobisocial.socialkit.musubi.DbFeed;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.DbUser;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.util.FastBase64;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

public class CorralClient {
    private static final String TAG = "corral";
    private static final boolean DBG = true;

    public static final String OBJ_MIME_TYPE = "mimeType";
    public static final String OBJ_LOCAL_URI = "localUri";
    static final long LOCAL_USER_ID = -666;
    static final String ATTR_LAN_IP = "vnd.mobisocial.device/lan_ip";
    static final int SERVER_PORT = 8224;

    private final Context mContext;

    public static CorralClient getInstance(Context context) {
        return new CorralClient(context);
    }

    private CorralClient(Context context) {
        mContext = context;
    }

    public boolean fileAvailableLocally(DbObj obj) {
        try {
        	DbUser dbUser = obj.getSender();
            long contactId = dbUser.getLocalId();
            if (contactId == LOCAL_USER_ID) {
                return true;
            }
            // Local
            return localFileForContent(obj).exists();
        } catch (Exception e) {
            Log.w(TAG, "Error checking file availability", e);
            return false;
        }
    }

    /**
     * Synchronized method that retrieves content by any possible transport, and
     * returns a uri representing it locally. This method blocks until the file
     * is available locally, or it has been determined that the file cannot
     * currently be fetched.
     */
    public Uri fetchContent(DbObj obj, Musubi mMusubi, String presenterIp) throws IOException {
        if (!obj.getJson().has(OBJ_LOCAL_URI)) {
            if (DBG) {
                Log.d(TAG, "no local uri for obj.");
            }
            return null;
        }
        String localId = obj.getContainingFeed().getLocalUser().getId();
        if (localId.equals(obj.getSender().getId())) {
            try {
                // TODO: Objects shared out from the content corral should
                // be accessible through the content corral. We don't have
                // to copy all files but we should have the option to create
                // a locate cache.
                return Uri.parse(obj.getJson().getString(OBJ_LOCAL_URI));
            } catch (JSONException e) {
                Log.e(TAG, "json exception getting local uri", e);
                return null;
            }
        }

        Uri feedName = DbFeed.uriForName(obj.getFeedName());
        DbUser user = mMusubi.userForGlobalId(feedName, obj.getSender().getId());
        File localFile = localFileForContent(obj);
        if (localFile.exists()) {
            return Uri.fromFile(localFile);
        }

        try {
            if (userAvailableOnLan(user) || presenterIp!=null) {
                return getFileOverLan(user, obj, presenterIp);
            } else {
                if (DBG) Log.d(TAG, "User not avaialable on LAN.");
            }
        } catch (IOException e) {
            if (DBG) Log.d(TAG, "Failed to pull LAN file", e);
        }

        try {
            return getFileOverBluetooth(user, obj);
        } catch (IOException e) {
        }

        if (!localFile.exists()) {
            throw new IOException("Failed to fetch file");
        }
        return Uri.fromFile(localFile);
    }

    private Uri getFileOverBluetooth(DbUser user, SignedObj obj)
            throws IOException {
        // TODO: implementation
        return null;
    }

    private Uri getFileOverLan(DbUser user, SignedObj obj, String presenterIp)
            throws IOException {
        try {
            // Remote
        	String ip;
        	if(presenterIp!=null){
        		ip = presenterIp;
        	}else{
        		ip = getUserLanIp(mContext, user);
        	}
            Uri remoteUri = uriForContent(ip, obj);
            URL url = new URL(remoteUri.toString());
            Log.d(TAG, "Attempting to pull file " + remoteUri);
            Log.d(TAG, "content: " + remoteUri.getQueryParameter("content"));

            File localFile = localFileForContent(obj);
            if (!localFile.exists()) {
                localFile.getParentFile().mkdirs();
                try {
                    InputStream is = url.openConnection().getInputStream();
                    OutputStream out = new FileOutputStream(localFile);
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    out.close();
                } catch (IOException e) {
                    if (localFile.exists()) {
                        localFile.delete();
                    }
                    throw e;
                }
            }
            return Uri.fromFile(localFile);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private boolean userAvailableOnLan(DbUser user) {
        // TODO: ipv6 compliance.
        // TODO: Try multiple ip endpoints; multi-sourced download;
        // torrent-style sharing
        // (mobile, distributed CDN)
    	return null != user.getAttribute(ATTR_LAN_IP);
    }


    private static Uri uriForContent(String host, SignedObj obj) {
        try {
            String localContent = obj.getJson().getString(OBJ_LOCAL_URI);
            Uri baseUri = Uri.parse("http://" + host + ":" + SERVER_PORT);
            return baseUri.buildUpon()
                    .appendQueryParameter("content", localContent)
                    .appendQueryParameter("hash", "" + obj.getHash()).build();
        } catch (Exception e) {
            Log.d(TAG, "No uri for content " + obj.getHash() + "; " + obj.getJson());
            return null;
        }
    }

    private static String getUserLanIp(Context context, DbUser user) {
        return user.getAttribute(ATTR_LAN_IP);
    }

    private File localFileForContent(SignedObj obj) {
        try {
            JSONObject json = obj.getJson();
            String suffix = suffixForType(json.optString(OBJ_MIME_TYPE));
            File feedDir = new File(mContext.getExternalCacheDir(), obj.getFeedName());
            String fname = hashToString(obj.getHash()) + "." + suffix;
            return new File(feedDir, fname);
        } catch (Exception e) {
            Log.e(TAG, "Error looking up file name", e);
            return null;
        }
    }

    public static String suffixForType(String type) {
        final String DEFAULT = "dat";
        if (type == null) {
            return DEFAULT;
        }
        if (type.equals("image/jpeg")) {
            return "jpg";
        }
        if (type.equals("video/3gpp")) {
            return "3gp";
        }
        if (type.equals("image/png")) {
            return "png";
        }
        return DEFAULT;
    }

    static String hashToString(long hash) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();  
            DataOutputStream dos = new DataOutputStream(bos);  
            dos.writeLong(hash);  
            dos.writeInt(-4);  
            byte[] data = bos.toByteArray();
            return FastBase64.encodeToString(data).substring(0, 11);
        } catch (IOException e) {
            return null;
        }
    }
}
