package com.cordova.upi;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class UPIPlugin extends CordovaPlugin {

    private static final String TAG = "UPIPLugin";
    private static final int REQUEST_CODE = 273849;

    private Map<String, String> APPLICATIONS = new HashMap<>();

    private String application;
    private CallbackContext callbackContext;

    protected void pluginInitialize() {
        this.APPLICATIONS.put("Paytm", "net.one97.paytm");
        this.APPLICATIONS.put("GooglePay", "com.google.android.apps.nbu.paisa.user");
        this.APPLICATIONS.put("BHIMUPI", "in.org.npci.upiapp");
        this.APPLICATIONS.put("PhonePe", "com.phonepe.app");
        this.APPLICATIONS.put("MiPay", "com.mipay.wallet.in");
        this.APPLICATIONS.put("AmazonPay", "in.amazon.mShop.android.shopping");
        this.APPLICATIONS.put("TrueCallerUPI", "com.truecaller");
        this.APPLICATIONS.put("MyAirtelUPI", "com.myairtelapp");
        this.APPLICATIONS.put("ICICI iMobile", "com.csam.icici.bank.imobile");
        this.APPLICATIONS.put("Canara Bank", "com.canarabank.mobility");
        this.APPLICATIONS.put("Yes Bank", "com.atomyes");
        this.APPLICATIONS.put("Kotak Bank for Tablet", "com.msf.kbank.tablet");
        this.APPLICATIONS.put("Kotak - 811", "com.msf.kbank.mobile");
        this.APPLICATIONS.put("HDFC Bank", "com.snapwork.hdfcbank");
        this.APPLICATIONS.put("Axis Mobile", "com.axis.mobile");
        this.APPLICATIONS.put("BHIM IndianBank UPI", "com.infrasofttech.indianbankupi");
        this.APPLICATIONS.put("BHIM Union Bank Pay", "com.fss.ubipsp");
        this.APPLICATIONS.put("BHIM PNB", "com.fss.pnbpsp");
        this.APPLICATIONS.put("BHIM SBI Pay", "com.sbi.upi");
        this.APPLICATIONS.put("YONO SBI", "com.sbi.lotusintouch");
        this.APPLICATIONS.put("Yono Lite SBI", "com.sbi.SBIFreedomPlus");
        this.APPLICATIONS.put("BOI Mobile", "com.boi.mpay");

    }

    private Activity getCurrentActivity() {
        return cordova.getActivity();
    }

    @Override
    public void onDestroy() {
        synchronized (this) {
            // destroy any components if needed
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("supportedApps")) {
            fetchSupportedApps(callbackContext);
            return true;
        } else if (action.equals("acceptPayment")) {
            JSONObject options = args.getJSONObject(0);
            acceptPayment(options, callbackContext);
            return true;
        }
        return false;
    }

    private void fetchSupportedApps(final CallbackContext callbackContext) {
        JSONArray result = new JSONArray();
        Iterator<Map.Entry<String, String>> entries = this.APPLICATIONS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, String> e = entries.next();
            if (isAvailable(e.getValue())) {
                result.put(e.getKey());
            }
        }
        callbackContext.success(result);
    }

    private void acceptPayment(JSONObject options, final CallbackContext callbackContext) {
        this.callbackContext = callbackContext;
        try {
            String app = options.getString("application");
            this.application = this.APPLICATIONS.get(app);
            if (!isAvailable(this.application)) {
                this.application = null;
            }
        } catch (JSONException exp) {
            this.application = null;
            Log.e(TAG, "There is no application information present in request context");
        }
        try {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(options.getString("upiString")));
            Context context = getCurrentActivity().getApplicationContext();
            if (this.application == null) {
                Intent chooser = Intent.createChooser(intent, "Pay using");
                if (chooser != null) {
                    cordova.startActivityForResult(this, chooser, REQUEST_CODE);
                } else {
                    callbackContext.error("no_upi_apps");
                }
            } else {
                Log.i(TAG, "Initiating payment using app " + this.application);
                intent.setPackage(application);
                cordova.startActivityForResult(this, intent, REQUEST_CODE);
            }
        } catch (JSONException exp) {
            Log.e(TAG, "There is no application information present in request context");
            callbackContext.error("malformed_upistring");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.i(TAG, "Request code " + requestCode + " resultCode " + resultCode);
        if (requestCode == REQUEST_CODE) {
            if (intent != null) {
                Log.i(TAG, "UPI payment response " + bundle2string(intent.getExtras()));
                try {
                    JSONObject result = new JSONObject();
                    result.put("status", intent.getStringExtra("Status"));
                    result.put("message", intent.getStringExtra("response"));
                    if (this.application != null) {
                        result.put("application", getApplicationName(this.application));
                    }
                    try {
                        parseUpiResponse(intent.getStringExtra("response"), result);
                        String status = result.getString("status");
                        this.callbackContext.success(result);
                    } catch (Exception exp) {
                        Log.e(TAG, "Issue in checking the status of  while parsing response from UPI callback", exp);
                        this.callbackContext.error("null_response");
                    }
                } catch (Exception exp) {
                    Log.e(TAG, "Exception while parsing response from UPI callback", exp);
                    this.callbackContext.error("null_response");
                }
            } else {
                try {
                    Log.d(TAG, "Data = null (User canceled)");
                    JSONObject result = new JSONObject();
                    result.put("status", "USER_CANCELLED");
                    this.callbackContext.error(result);
                } catch (Exception exp) {
                    Log.e(TAG, "Exception while sending error response", exp);
                    this.callbackContext.error("null_response");
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    private boolean isAvailable(String bundleId) {
        PackageManager pm = getCurrentActivity().getPackageManager();
        try {
            pm.getPackageInfo(bundleId, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(TAG, bundleId + " is not present in mobile");
        }
        return false;
    }

    private void parseUpiResponse(String upi_response, JSONObject json) throws JSONException {
        String[] _parts = upi_response.split("&");
        for (int i = 0; i < _parts.length; ++i) {
            String key = _parts[i].split("=")[0];
            String value = _parts[i].split("=")[1];
            json.put(key, value);
            if (key.toLowerCase() == "status") {
                json.put("status", value);
            }
        }
    }

    private String getApplicationName(String bundleId) {
        if (this.APPLICATIONS.containsValue(bundleId)) {
            for (String key : this.APPLICATIONS.keySet()) {
                String v = this.APPLICATIONS.get(key);
                if (v == bundleId) {
                    return key;
                }
            }
        }
        return bundleId;
    }

    private String bundle2string(Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        String string = "Bundle{";
        for (String key : bundle.keySet()) {
            string += " " + key + " => " + bundle.get(key) + ";";
        }
        string += " }Bundle";
        return string;
    }
}