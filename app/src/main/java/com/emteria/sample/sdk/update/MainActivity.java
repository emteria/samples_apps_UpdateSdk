package com.emteria.sample.sdk.update;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.emteria.update.contract.MessengerConfig;
import com.emteria.update.contract.exceptions.InvalidUpdatePayloadException;
import com.emteria.update.contract.managers.OsVersionContract;
import com.emteria.update.contract.managers.UpdateDownloadContract;
import com.emteria.update.contract.managers.UpdateInstallationContract;
import com.emteria.update.contract.managers.UpdateMetadataContract;
import com.emteria.update.contract.models.UpdatePackage;

import java.util.List;

public class MainActivity extends AppCompatActivity
{
    private static final String TAG = "MainActivity";

    /**
     * Messenger for sending requests to the remote service.
     */
    private Messenger mRequestMessenger;

    /**
     * Messenger for receiving callback messages.
     */
    private Messenger mResponseMessenger;

    private boolean mBound = false;

    private Button mFindUpdateButton;
    private Button mDownloadButton;
    private Button mFlashButton;
    private TextView mInformationTV;
    private ProgressBar mProgressBar;

    private UpdatePackage mOtaPackage;

    private final ServiceConnection mConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder)
        {
            Log.i(TAG, "Service is connected to the activity");
            mBound = true;
            mRequestMessenger = new Messenger(binder);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0)
        {
            Log.i(TAG, "Service disconnected");
            mBound = false;
            mRequestMessenger = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindUpdateService();

        mFindUpdateButton = findViewById(R.id.get_update_button);
        mDownloadButton = findViewById(R.id.download_update_button);
        mFlashButton = findViewById(R.id.flash_device_button);
        mInformationTV = findViewById(R.id.version_details);
        mProgressBar = findViewById(R.id.progressBar);

        mFindUpdateButton.setEnabled(true);
        mDownloadButton.setEnabled(false);
        mFlashButton.setEnabled(false);

        mResponseMessenger = new Messenger(new CallbackHandler());
    }

    private void bindUpdateService()
    {
        Log.d(TAG, "bindUpdateService()");
        if (!mBound)
        {
            boolean binding = bindService(MessengerConfig.getServiceBindIntent(), mConnection, Context.BIND_AUTO_CREATE);
            Log.i(TAG, "Service bind result: " + binding);
        }
    }

    private void unbindUpdateService()
    {
        Log.d(TAG, "unbindUpdateService()");
        if (mBound)
        {
            unbindService(mConnection);
            mBound = false;
            mRequestMessenger = null;
        }
    }

    public void getOsVersion(View v)
    {
        Log.i(TAG, "Get OS version");

        if (!mBound)
        {
            mInformationTV.setText("Service not bound, try again");
            Log.e(TAG, "Service not bound");
            return;
        }

        try
        {
            mInformationTV.setText("Getting OS version...");
            mProgressBar.setIndeterminate(true);

            Message requestMessage = OsVersionContract.OsVersionRequest.buildMessage(mResponseMessenger);
            mRequestMessenger.send(requestMessage);
        }
        catch (RemoteException e)
        {
            mInformationTV.setText("RemoteException: " + e.getMessage());
            Log.e(TAG, "RemoteException", e);
        }
    }

    public void startOtaSearch(View v)
    {
        Log.i(TAG, "Get available updates");

        if (!mBound)
        {
            mInformationTV.setText("Service not bound, try again");
            Log.e(TAG, "Service not bound");
            return;
        }

        try
        {
            mInformationTV.setText("Getting updates...");
            mProgressBar.setIndeterminate(true);

            Message requestMessage = UpdateMetadataContract.MetadataRequest.buildMessage(mResponseMessenger, false);
            mRequestMessenger.send(requestMessage);
        }
        catch (RemoteException e)
        {
            mInformationTV.setText("RemoteException: " + e.getMessage());
            Log.e(TAG, "RemoteException", e);
        }
    }

    public void startOtaDownload(View v)
    {
        Log.i(TAG, "Download update " + mOtaPackage);

        if (!mBound)
        {
            mInformationTV.setText("Service not bound, try again");
            Log.e(TAG, "Service not bound");
            return;
        }

        try
        {
            mInformationTV.setText("Downloading update");
            mDownloadButton.setEnabled(false);
            mProgressBar.setIndeterminate(true);
            Message requestMessage = UpdateDownloadContract.DownloadRequest.buildMessage(mResponseMessenger, mOtaPackage.getUpdateId());
            mRequestMessenger.send(requestMessage);
        }
        catch (RemoteException e)
        {
            mInformationTV.setText("RemoteException");
            Log.e(TAG, "RemoteException", e);
        }
    }

    public void startOtaInstallation(View v)
    {
        Log.i(TAG, "Install update " + mOtaPackage);

        if (!mBound)
        {
            mInformationTV.setText("Service not bound, try again");
            Log.e(TAG, "Service not bound");
            return;
        }

        try
        {
            mInformationTV.setText("Flashing update, the device would normally reboot to update.");
            Message requestMessage = UpdateInstallationContract.InstallationRequest.buildMessage(mResponseMessenger, mOtaPackage.getUpdateId());
            mRequestMessenger.send(requestMessage);
        }
        catch (RemoteException e)
        {
            mInformationTV.setText("RemoteException");
            Log.e(TAG, "RemoteException", e);
        }
    }

    @Override
    protected void onDestroy()
    {
        Log.i(TAG, "onDestroy()");
        super.onDestroy();
        unbindUpdateService();
    }

    private class CallbackHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg)
        {
            MessengerConfig.ResponseReason[] values = MessengerConfig.ResponseReason.values();

            int index = msg.what;
            if (index < 0 || index >= values.length)
            {
                Log.e(TAG, "Handler received unknown message. Message processing aborted.");
                return;
            }

            MessengerConfig.ResponseReason reason = values[index];
            Bundle payload = msg.getData();

            mProgressBar.setIndeterminate(false);
            Log.i(TAG, "Received message " + reason);

            try
            {
                switch (reason)
                {
                    case GET_OS_VERSION_ERROR:
                        String versionError = OsVersionContract.OsVersionErrorResponse.extractErrorMessage(payload);
                        mInformationTV.setText("Could not receive OS version: " + versionError);
                        break;

                    case GET_OS_VERSION_RESULT:
                        String versionResult = OsVersionContract.OsVersionResultResponse.extractOsVersion(payload);
                        mInformationTV.setText("Current OS version: " + versionResult);
                        break;

                    case GET_UPDATES_UP_TO_DATE:
                        mInformationTV.setText("Your OS is up to date");
                        break;

                    case GET_UPDATES_ERROR:
                        String retrievalError = UpdateMetadataContract.MetadataErrorResponse.extractErrorMessage(payload);
                        mInformationTV.setText("Could not retrieve updates: " + retrievalError);
                        break;

                    case GET_UPDATES_LIST:
                        List<UpdatePackage> updateList = UpdateMetadataContract.MetadataListResponse.extractUpdateList(payload);
                        for (UpdatePackage updatePackage : updateList) { Log.i(TAG, "Available update: " + updatePackage); }
                        mOtaPackage = updateList.get(0);
                        String versionText = getString(R.string.updates_message, String.valueOf(updateList.size()), mOtaPackage.getVersion(), mOtaPackage.getChannel(), String.valueOf(mOtaPackage.getFileSize()));
                        mInformationTV.setText(Html.fromHtml(versionText, Html.FROM_HTML_MODE_LEGACY));
                        mDownloadButton.setEnabled(true);
                        break;

                    case DOWNLOAD_UPDATE_ERROR:
                        String downloadError = UpdateDownloadContract.DownloadErrorResponse.extractErrorMessage(payload);
                        mInformationTV.setText("Download failed: " + downloadError);
                        break;

                    case DOWNLOAD_UPDATE_PROGRESS:
                        int downloadProgress = UpdateDownloadContract.DownloadProgressResponse.extractDownloadProgress(payload);
                        setProgress(downloadProgress);
                        break;

                    case DOWNLOAD_UPDATE_SUCCESS:
                        String downloadedUpdateId = UpdateDownloadContract.DownloadSuccessResponse.extractUpdateId(payload);
                        Log.i(TAG, "OTA download finished for " + downloadedUpdateId);
                        mInformationTV.setText("Download successful");
                        mFindUpdateButton.setEnabled(true);
                        mDownloadButton.setEnabled(false);
                        mFlashButton.setEnabled(true);
                        break;

                    case INSTALL_UPDATE_ERROR:
                        String installationError = UpdateInstallationContract.InstallationErrorResponse.extractErrorMessage(payload);
                        mInformationTV.setText("Installation failed: " + installationError);
                        break;

                    case INSTALL_UPDATE_PROGRESS:
                        int installProgress = UpdateInstallationContract.InstallationProgressResponse.extractInstallationProgress(payload);
                        setProgress(installProgress);
                        break;

                    case INSTALL_UPDATE_REBOOT_REQUIRED:
                        mInformationTV.setText("Installation complete, reboot required");
                        mFlashButton.setEnabled(false);
                        break;

                    default:
                        mInformationTV.setText("Unknown message " + reason.name());
                        Log.e(TAG, "Unknown message");
                }
            }
            catch (InvalidUpdatePayloadException e)
            {
                Log.e(TAG, "Invalid message payload " + e.getMessage());
                mInformationTV.setText("Error " + e.getMessage());
            }
        }

        private void setProgress(int progress)
        {
            boolean indeterminate = (progress == -1);
            if (indeterminate) { progress = 0; }

            if (mProgressBar != null)
            {
                mProgressBar.setIndeterminate(indeterminate);
                mProgressBar.setProgress(progress);
            }
        }
    }
}
