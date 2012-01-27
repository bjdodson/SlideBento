package com.kazus.android.slidebento.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.teleal.cling.android.AndroidUpnpService;
import org.teleal.cling.android.AndroidUpnpServiceImpl;
import org.teleal.cling.model.action.ActionInvocation;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.message.header.STAllHeader;
import org.teleal.cling.model.meta.Device;
import org.teleal.cling.model.meta.Service;
import org.teleal.cling.model.types.UDAServiceId;
import org.teleal.cling.registry.Registry;
import org.teleal.cling.support.igd.PortMappingListener;
import org.teleal.cling.support.igd.callback.GetExternalIP;
import org.teleal.cling.support.igd.callback.PortMappingAdd;
import org.teleal.cling.support.igd.callback.PortMappingDelete;
import org.teleal.cling.support.model.PortMapping;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

public class UpnpController{
	
	private static final String TAG = "UpnpController";
    private String localIp;
    private PortMapping desiredMapping;
    private AndroidUpnpService upnpService;
    private Device NWdevice;
    private Context mContext;
    private int     mPort;
    
    public UpnpController(Context context, int port){
    	mContext = context;
    	mPort = port;
        localIp = getLocalIpAddress();
        desiredMapping =
                new PortMapping(
                        mPort,
                        localIp,
                        PortMapping.Protocol.TCP,
                        "My Port Mapping"
                );
    }
    
    public void startService() {
        mContext.bindService(
                new Intent(mContext, AndroidUpnpServiceImpl.class),
                serviceConnection,
                Context.BIND_AUTO_CREATE
        );
    }
    // callback after port closed
    public void onPortClosed(){
    }
    // callback after port open and ext IP address
	public void onGetExternalIP(String ipaddress){
	}
	public void stopService(){
	    mContext.unbindService(serviceConnection);
	}
	
    private ServiceConnection serviceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            upnpService = (AndroidUpnpService) service;
            openPort();
        }

        public void onServiceDisconnected(ComponentName className) {
            upnpService = null;
        }
    };

    private class MyPortMappingListener extends PortMappingListener {

		public MyPortMappingListener(PortMapping portMapping) {
			super(portMapping);
			// TODO Auto-generated constructor stub
		}
		
	    @Override
	    synchronized public void deviceAdded(Registry registry, final Device device) {
	    	
	        Service connectionService;
	        if ((connectionService = discoverConnectionService(device)) == null) return;

	        Log.d(TAG, "Activating port mappings on: " + connectionService);

	        final List<PortMapping> activeForService = new ArrayList();
	        for (final PortMapping pm : portMappings) {
	            new PortMappingAdd(connectionService, registry.getUpnpService().getControlPoint(), pm) {

	                @Override
	                public void success(ActionInvocation invocation) {
	                    Log.d(TAG, "Port mapping added: " + pm);
	                    activeForService.add(pm);
	                    
	                    NWdevice = device;

			            getExternalIP();
	                }

	                @Override
	                public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
	                    handleFailureMessage("Failed to add port mapping: " + pm);
	                    handleFailureMessage("Reason: " + defaultMsg);
	                }
	            }.run(); // Synchronous!
	        }

	        activePortMappings.put(connectionService, activeForService);
	    }

    }

	public void openPort(){
		Log.d(TAG, "Start to open port...");
		if(NWdevice == null){
	        upnpService.getRegistry().addListener(new MyPortMappingListener(desiredMapping));
	        upnpService.getControlPoint().search(new STAllHeader());
		}else{
			Service service = NWdevice.findService(new UDAServiceId("WANIPConnection"));
			upnpService.getControlPoint().execute(
				    new PortMappingAdd(service, desiredMapping) {

				        @Override
				        public void success(ActionInvocation invocation) {
				            getExternalIP();
				        }

				        @Override
				        public void failure(ActionInvocation invocation,
				                            UpnpResponse operation,
				                            String defaultMsg) {
				            // Something is wrong
				        }
				    }
				);
		}
	}
	
	public void getExternalIP(){
        Service service = NWdevice.findService(new UDAServiceId("WANIPConnection"));
        if(service == null){
        	return;
        }
        
        upnpService.getControlPoint().execute(
            new GetExternalIP(service) {

                @Override
                protected void success(String externalIPAddress) {
                    Log.e(TAG, externalIPAddress);
                    onGetExternalIP(externalIPAddress);
//                    showToast("Suceed to set portmapping! ExternalIP:"+externalIPAddress+", LocalIP: "+localIp+" Port:8224", true);
                }

                @Override
                public void failure(ActionInvocation invocation,
                                    UpnpResponse operation,
                                    String defaultMsg) {
                    // Something is wrong
                }
            }
        );
	}
	
	public void closePort(){
		if(NWdevice == null || upnpService ==null){
			Log.e(TAG, "closedPort() called with NULL object");
			return;
		}
		
		Service service = NWdevice.findService(new UDAServiceId("WANIPConnection"));
		if(service==null){
			return;
		}
		
		upnpService.getControlPoint().execute(
			    new PortMappingDelete(service, desiredMapping) {

			        @Override
			        public void success(ActionInvocation invocation) {
			            // All OK
//		                showToast("Suceed to remove portmapping!", true);
		                upnpService = null;
		                onPortClosed();
			        }

			        @Override
			        public void failure(ActionInvocation invocation,
			                            UpnpResponse operation,
			                            String defaultMsg) {
			            // Something is wrong
		                upnpService = null;
			        }
			    }
			);
    }

    // ------------------------
    // functions
    // ------------------------
    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(TAG, ex.toString());
        }
        return null;
    }
    public int getPort(){
    	return mPort;
    }
}