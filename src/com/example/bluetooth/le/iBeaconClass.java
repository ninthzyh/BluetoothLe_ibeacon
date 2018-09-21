package com.example.bluetooth.le;

import java.util.ArrayList;
import java.util.Arrays;



import android.bluetooth.BluetoothDevice;
import android.graphics.Point;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * 代码改自https://github.com/RadiusNetworks/android-ibeacon-service/blob/master/src/main/java/com/radiusnetworks/ibeacon/IBeacon.java
 * @author gvzhang
 *
 */
public class iBeaconClass {

    public static Cursor cursor;
    public static class iBeacon{
    	public String name;
    	public int major;
    	public int minor;
    	public String proximityUuid;
    	public String bluetoothAddress;
    	public int txPower;
    	public int rssi;
    	public double distance;
//    	public double corDistance;
//    	public double[] distanceMap;
//    	protected int getRssi(){
//    		return rssi;
//    	}
//    	protected double getDistance(){
//    		return distance;
 //  	}

    	
		
    }
    
   
    public static iBeacon fromScanData(BluetoothDevice device, int rssi,byte[] scanData) {
    	
    	int startByte = 2;
		boolean patternFound = false;
//		DeviceScanActivity.dFlag  = 0;
		while (startByte <= 5) {
			if (((int)scanData[startByte+2] & 0xff) == 0x02 &&
				((int)scanData[startByte+3] & 0xff) == 0x15) {			
				// yes!  This is an iBeacon	
				patternFound = true;
				break;
			}
			else if (((int)scanData[startByte] & 0xff) == 0x2d &&
					((int)scanData[startByte+1] & 0xff) == 0x24 &&
					((int)scanData[startByte+2] & 0xff) == 0xbf &&
					((int)scanData[startByte+3] & 0xff) == 0x16) {
                iBeacon iBeacon = new iBeacon();
				iBeacon.major = 0;
				iBeacon.minor = 0;
				iBeacon.proximityUuid = "00000000-0000-0000-0000-000000000000";
				iBeacon.txPower = -55;
				iBeacon.distance = 0 ;
				//iBeacon.corDistance=0;
				return iBeacon;
			}
            else if (((int)scanData[startByte] & 0xff) == 0xad &&
                     ((int)scanData[startByte+1] & 0xff) == 0x77 &&
                     ((int)scanData[startByte+2] & 0xff) == 0x00 &&
                     ((int)scanData[startByte+3] & 0xff) == 0xc6) {
                    iBeacon iBeacon = new iBeacon();
                    iBeacon.major = 0;
                    iBeacon.minor = 0;
                    iBeacon.proximityUuid = "00000000-0000-0000-0000-000000000000";
                    iBeacon.txPower = -55;
                    iBeacon.distance = 0 ;
                    //iBeacon.corDistance = 0;
                    return iBeacon;
            }
			startByte++;
		}


		if (patternFound == false) {
			// This is not an iBeacon
			return null;
		}

		iBeacon iBeacon = new iBeacon();

		iBeacon.major = (scanData[startByte+20] & 0xff) * 0x100 + (scanData[startByte+21] & 0xff);
		iBeacon.minor = (scanData[startByte+22] & 0xff) * 0x100 + (scanData[startByte+23] & 0xff);
		iBeacon.txPower = (int)scanData[startByte+24]; // this one is signed
 		if (device != null) {
			iBeacon.bluetoothAddress = device.getAddress();
			iBeacon.name = device.getName();
		}

		// TODO: 2017/6/2
//		if (DeviceScanActivity.dFlag == 1){
//			database.delete("btrssi", null, null);
//		}
//		//将相应设备的rssi写入到数据库
//		//创建数据库
//		database = openHelper.getWritableDatabase();
//		ContentValues values = new ContentValues();
//		values.put("device",device.getAddress());
//		values.put("rssi",rssi);
//		database.insert("btrssi", null, values);
//		//从数据库将相应设备的rssi提出并取平均值
//		cursor = database.query(true, "btrssi", new String[]{"_id", "device", "rssi"}, null, null, null, null, null, null, null);
//		int rSum = 0;
//		int rI = 0;
//		while (cursor.moveToNext()){
//			String dTemp = cursor.getString(1);
//			int rTemp = cursor.getInt(2);
//			if (dTemp.equals(device.getAddress())){
//				rSum += rTemp;
//				rI += 1;
//			}
//		}
//		int rssiAve = rSum/rI;
//		Log.e("rssi", rssi+"");
//		Log.e("rssiAve",rssiAve+"");
//		// TODO: 2017/6/2
		iBeacon.rssi = rssi;
		iBeacon.distance = calculateAccuracy(iBeacon.rssi);
		
		//iBeacon.corDistance=corrected(iBeacon);
		
		// AirLocate:
		// 02 01 1a 1a ff 4c 00 02 15  # Apple's fixed iBeacon advertising prefix
		// e2 c5 6d b5 df fb 48 d2 b0 60 d0 f5 a7 10 96 e0 # iBeacon profile uuid
		// 00 00 # major 
		// 00 00 # minor 
		// c5 # The 2's complement of the calibrated Tx Power

		// Estimote:		
		// 02 01 1a 11 07 2d 24 bf 16 
		// 394b31ba3f486415ab376e5c0f09457374696d6f7465426561636f6e00000000000000000000000000000000000000000000000000

		byte[] proximityUuidBytes = new byte[16];
		System.arraycopy(scanData, startByte+4, proximityUuidBytes, 0, 16); 
		String hexString = bytesToHexString(proximityUuidBytes);
		StringBuilder sb = new StringBuilder();
		sb.append(hexString.substring(0,8));
		sb.append("-");
		sb.append(hexString.substring(8,12));
		sb.append("-");
		sb.append(hexString.substring(12,16));
		sb.append("-");
		sb.append(hexString.substring(16,20));
		sb.append("-");
		sb.append(hexString.substring(20,32));
		iBeacon.proximityUuid = sb.toString();

        if (device != null) {
            iBeacon.bluetoothAddress = device.getAddress();
            iBeacon.name = device.getName();
        }

		return iBeacon;
		

    }

	public static double calculateAccuracy(int rssi) {
		double temp =Math.abs(rssi);
		double distance2 = 0;
		double accuracy = 0;
		if (rssi == 0) {
			return -1.0; // if we cannot determine accuracy, return -1.
		} 
		
		else{
			accuracy = (temp-59)/(10*3.8);
			distance2 = (double)(Math.round(Math.pow(10.0, accuracy)*100)/100.0);
			return distance2;
		}
		}
//	public static double corrected(iBeacon device){
//		double corDistance;
//		double temp1 =0;
//		double temp2 =0;
//		temp1=calculateAccuracy(device.getRssi());
//		temp2 =calculateAccuracy(device.getRssi());
//		corDistance=(temp1+temp2)/2;
//		return corDistance;
//	}
//    protected static double calculateAccuracy( int rssi) {  
//    	  if (rssi == 0) {  
//    	    return -1.0; // if we cannot determine accuracy, return -1.  
//    	  }  
//    	  
//    	  double ratio = rssi*1.0/59.0;  
//    	  if (ratio < 1.0) {  
//    	    return (double)(Math.round(Math.pow(ratio,10)*100)/100.0);  
//    	  }  
//    	  else {  
//    	    double accuracy = (double)(Math.round(((0.89976)*Math.pow(ratio,7.7095) + 0.111)*100)/100.0);      
//    	    return accuracy;  
//    	  }  
//    	}     
//	  public static double calculateAccuracy(int rssi) {
//	       if (rssi == 0) {
//	           return -1.0; // if we cannot determine accuracy, return -1.
//	       }
//	       double ratio = rssi * 1.0 / 59.0;
//	       if (ratio < 1.0) {
//	           return (double)(Math.round(Math.pow(ratio, 10)*100)/100.0);
//	       } else {
//	           double accuracy = (double)(Math.round(((0.42093) * Math.pow(ratio, 6.9476) + 0.54992)*100)/100.0);
//	           return accuracy;
//	       }
//	   }
	 
    private static String bytesToHexString(byte[] src){  
        StringBuilder stringBuilder = new StringBuilder("");  
        if (src == null || src.length <= 0) {  
            return null;  
        }  
        for (int i = 0; i < src.length; i++) {  
            int v = src[i] & 0xFF;  
            String hv = Integer.toHexString(v);  
            if (hv.length() < 2) {  
                stringBuilder.append(0);  
            }  
            stringBuilder.append(hv);  
        }  
        return stringBuilder.toString();  
    }  

 }    
    	