package nl.timeseries.plugin.pm80;

import org.apache.cordova.*;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.*;

import java.lang.*;

import java.security.Key;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import android.content.Context;
import android.util.Log;

import device.common.MsrIndex;
import device.common.MsrResult;
import device.common.MsrResultCallback;
import device.sdk.MsrManager;

public class PM80 extends CordovaPlugin {
    private static final String TAG="MSR";

    /* read state */
    public static final int READ_SUCCESS=0;
    public static final int READ_FAIL=1;
    public static final int READ_READY=2;

    private static MsrManager mMsr=null;
    private MsrResult mDetectResult=null;

    private String mTrack1;
    private String mTrack2;
    private String mTrack3;

    private String mResult = null;

    private boolean readerActivated = false;

    /***************************************************
     * LIFECYCLE
     ***************************************************/


    /**
     * Called after plugin construction and fields have been initialized.
     */
    @Override
    public void initialize(CordovaInterface cordova,CordovaWebView webView){
        super.initialize(cordova,webView);
        mDetectResult = new MsrResult();
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     * The reader is killed and MSR is unregistered.
     *
     * @param multitasking
     *      Flag indicating if multitasking is turned on for app
     */
    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);

        if (readerActivated) {
            deactivateReader(null);
        }
    }

    /**
     * Called when the activity will start interacting with the user.
     * The reader is reinitialized as if the app was just opened.
     *
     * @param multitasking
     *      Flag indicating if multitasking is turned on for app
     */
    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);

        if (readerActivated) {
            activateReader(null);
        }
    }

    /***************************************************
     * JAVASCRIPT INTERFACE IMPLEMENTATION
     ***************************************************/

    /**
     * Executes the request sent from JavaScript.
     *
     * @param action
     *      The action to execute.
     * @param args
     *      The exec() arguments in JSON form.
     * @param command
     *      The callback context used when calling back into JavaScript.
     * @return
     *      Whether the action was valid.
     */
    @Override
    public boolean execute(final String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if("MSR_activateReader".equals(action)){
            activateReader(callbackContext);
        } else if("MSR_deactivateReader".equals(action)){
            deactivateReader(callbackContext);
        } else if("MSR_swipe".equals(action)){
            swipe(callbackContext);
        } else if("MSR_stopSwipe".equals(action)){
            stopSwipe(callbackContext);
        } else {
            // Method not found.
            return false;
        }
        return true;
    }

    /**
     * Initializes registers the Headset Receiver for headset detection, and starts
     * listen to SDK events for connection, disconnection, swiping, etc.
     *
     * @param callbackContext
     *        Used when calling back into JavaScript
     */
    private void activateReader(final CallbackContext callbackContext){
        String callbackContextMsg = null;

        try{
            mMsr = new MsrManager();
            if(mMsr != null){
                mMsr.DeviceMsrOpen(mCallback);
            }
            mTrack1 = new String();
            mTrack2 = new String();
            mTrack3 = new String();

            if(callbackContext != null){
                readerActivated = true;
            }
        } catch(IllegalArgumentException e){
            e.printStackTrace();
            callbackContextMsg = "Failed to activate reader";
        }
        sendCallback(callbackContext,callbackContextMsg);
    }

    private void deactivateReader(final CallbackContext callbackContext){
        try{
            mMsr.DeviceMsrClose();
            if(callbackContext != null){
                readerActivated = false;
            }
        } catch(IllegalArgumentException e){
            e.printStackTrace();
        }

        sendCallback(callbackContext,null);
    }
    /**
     * Tells the SDK to begin expecting a swipe. From the moment this is
     * called, the user will have 30 seconds to swipe the card before a
     * timeout error occurs.
     *
     * @param callbackContext
     *        Used when calling back into JavaScript
     */
    private void swipe(final CallbackContext callbackContext) {
        if(mMsr != null) {
            if (mMsr.DeviceMsrStartRead() > 0) {
                // If we get this far, we can expect events for card
                // processing and card data received if a card is
                // actually swiped, otherwise we can expect a timeout
                // event.
                callbackContext.success();
            } else {
                // Unexpected error
                callbackContext.error("Failed to start swipe.");
            }

        } else callbackContext.error("Reader must be activated before starting swipe.");
    }
    /**
     * Tells the SDK to stop expecting a swipe.
     *
     * @param callbackContext
     *        Used when calling back into JavaScript
     */
    private void stopSwipe(final CallbackContext callbackContext) {
        if(mMsr != null) {
            mMsr.DeviceMsrStopRead();
        } else callbackContext.error("Reader must be activated before stopping swipe.");
    }

    /***************************************************
     * SDK CALLBACKS
     ***************************************************/
    MsrResultCallback mCallback = new MsrResultCallback() {
        @Override
        public void onResult(int cmd, int status) {
            int readstatus = status & 0xff;
            if (readstatus == 0) {
                GetResult();
                String message;
                message = "{\"Track1\":\"" + mTrack1 + "\",\"Track2\":\"" + mTrack2 + "\",\"Track3\":\"" + mTrack3 + "\"}";
                fireEvent("swipe_success", message);
            } else {
                fireEvent("swipe_failed",errormsg(status, readstatus));
            }
            mTrack1 = new String();
            mTrack2 = new String();
            mTrack3 = new String();
        }
    };

    /***************************************************
     * UTILS
     ***************************************************/

    String errormsg(int track, int status)
    {
        String msg = new String();
        if (status == MsrIndex.MMD1000_READ_OK) {
            msg = "Success";
            if ((track&0x600) == 0x600) {
                msg += " part(Track2,3 fail)";
            } else if ((track&0x500) == 0x500) {
                msg += " part(Track1,3 fail)";
            } else if ((track&0x300) == 0x300) {
                msg += " part(Track1,2 fail)";
            } else if ((track&0x100) == 0x100) {
                msg += " part(track1 fail)";
            } else if ((track&0x200) == 0x200) {
                msg += " part(track2 fail)";
            } else if ((track&0x400) == 0x400) {
                msg += " part(track3 fail)";
            }
            else {
                msg += " all";
            }
        }
        else {
            switch(status) {
                case MsrIndex.MMD1000_READ_ERROR:				msg = "Read failed";		break;
                case MsrIndex.MMD1000_CRC_ERROR:				msg = "CRC error in encryption related information stored in OTP";		break;
                case MsrIndex.MMD1000_NOINFOSTORE:				msg = "No information stored in OTP related to encryption";		break;
                case MsrIndex.MMD1000_AES_INIT_NOT_SET:			msg = "AES initial vector is not set yet";		break;
                case MsrIndex.MMD1000_READ_PREAMBLE_ERROR:		msg = "Preamble error in card read data";		break;
                case MsrIndex.MMD1000_READ_POSTAMBLE_ERROR:		msg = "Postamble error in card read data";		break;
                case MsrIndex.MMD1000_READ_LRC_ERROR:			msg = "LRC error in card read data";		break;
                case MsrIndex.MMD1000_READ_PARITY_ERROR:		msg = "Parity error in card read data";		break;
                case MsrIndex.MMD1000_BLANK_TRACK:				msg = "Black track";		break;
                case MsrIndex.MMD1000_CMD_STXETX_ERROR:			msg = "STX/ETX error in command communication";		break;
                case MsrIndex.MMD1000_CMD_UNRECOGNIZABLE:		msg = "Class/Function un-recognizable in command";		break;
                case MsrIndex.MMD1000_CMD_BCC_ERROR:			msg = "BCC error in command communication";		break;
                case MsrIndex.MMD1000_CMD_LENGTH_ERROR:			msg = "Length error in command communication";		break;
                case MsrIndex.MMD1000_READ_NO_DATA:				msg = "No data available to re-read";		break;
                case MsrIndex.MMD1000_DEVICE_READ_TIMEOUT:		msg = "Read command timeout";		break;
                case MsrIndex.MMD1000_DEVICE_POWER_DISABLE:		msg = "MMD1000 power is disable";		break;
                case MsrIndex.MMD1000_DEVICE_NOT_OPENED:		msg = "MMD1000 function is not opened";		break;
                case MsrIndex.MMD1000_DEVICE_DATA_CLEARED:		msg = "MMD1000 device result is cleared";		break;
                default:		msg = "error"; 		break;
            }
        }

        return msg;
    }


    public boolean setUsedEncryption() {
        byte[] keySerialNumber = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x00, 0x00, 0x00};
        byte[] initialKey = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10};
        return mMsr.setUsedEncryption(keySerialNumber, initialKey);
    }
    public byte[] getEncryptionData() {
        return mMsr.getEncryptionData();
    }
    private byte[] getDukptKey() {
        byte[] data = getEncryptionData();
        byte[] initialKey = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10};
        byte[] ksn = new byte[10];
        for (int i = 0; i < 10; i++) {
            ksn[i] = data[5 + i];
        }
        long tc = ((ksn[7] & 0x1f) << 16) | (ksn[8] & 0xff) <<8 | ksn[9] & 0xff;
        return Dukpt.getKey(initialKey, ksn, tc);
    }

    private byte[] getDataFromEncryptionData() {
        byte[] data = getEncryptionData();
        int padLength = data[2] & 0xFF;
        int dataLength = (((data[3] & 0xFF) << 8) | (data[4] & 0xFF)) + padLength - 10;
        byte[] encryptedData = new byte[dataLength];
        System.arraycopy(data, 15, encryptedData, 0, dataLength);
        return encryptedData;
    }
    public byte[] getDecryptionData() {
        byte[] decoded = null;
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("AES/CBC/NoPadding");
            Key keySpec = new SecretKeySpec(getDukptKey(), "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            decoded = cipher.doFinal(getDataFromEncryptionData());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return decoded;
    }
    public void GetResult() {
        if (mMsr != null) {
            byte[] encryptionData = getEncryptionData();
            if (encryptionData != null) {
                String s = "";
                for (byte b : encryptionData) {
                    s += String.format("%02X", b);
                }
                // Encrypted data.
                mTrack1 = s;

                byte[] decryptionData = getDecryptionData();
                s = "";
                if (decryptionData != null) {
                    for (byte b : decryptionData) {
                        s += String.format("%02X", b);
                    }
                }
                // Decrypted data.
                mTrack2 = s;
            } else {
                mDetectResult = mMsr.DeviceMsrGetData(0x07);
                mTrack1 = mDetectResult.getMsrTrack1();
                mTrack2 = mDetectResult.getMsrTrack2();
                mTrack3 = mDetectResult.getMsrTrack3();
            }
        }
    }
    /**
     * Perform either a success or error callback on given CallbackContext
     * depending on state of msg.
     * @param callbackContext
     *        Used when calling back into JavaScript
     * @param msg
     *        Error message, or null if success
     */

    private void sendCallback(CallbackContext callbackContext, String msg) {
        // callbackContext will only be null when caller called from
        // lifecycle methods (i.e., never from containing app).
        if (callbackContext != null) {
            if (msg == null) {
                callbackContext.success();
            } else callbackContext.error(msg);
        }
    }

    /**
     * Pass event to method overload.
     *
     * @param event
     *        The event name
     */
    private void fireEvent(String event) {
        fireEvent(event, null);
    }

    /**
     * Format and send event to JavaScript side.
     *
     * @param event
     *        The event name
     * @param data
     *        Details about the event
     */
    private void fireEvent(String event, String data) {
        if(data != null) {
            data = data.replaceAll("\\s","");
        }
        String dataArg = data != null ? "','" + data + "" : "";

        String js = "cordova.plugins.pm80.fireEvent('" +
                event + dataArg + "');";

        webView.sendJavascript(js);
    }
}