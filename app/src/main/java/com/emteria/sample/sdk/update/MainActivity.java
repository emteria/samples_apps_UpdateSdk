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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.emteria.update.contract.MessengerConfig;
import com.emteria.update.contract.managers.OtaDownloadContract;
import com.emteria.update.contract.managers.OtaInstallationContract;
import com.emteria.update.contract.managers.OtaMetadataContract;
import com.emteria.update.contract.managers.OtaPreferenceContract;
import com.emteria.update.contract.models.OtaChannel;
import com.emteria.update.contract.models.OtaPackage;

public class MainActivity extends AppCompatActivity
{
    private static final String TAG = "ExternalUpdateSample - MainActivity";

    /**
     * Messenger for sending requests to the Service
     */
    private Messenger mRequestMessenger;
    /**
     * Messenger for receiving callback messages
     */
    private Messenger mResponseMessenger;

    private boolean mBound = false;

    private Button mHandleUpdateButton;
    private Button mDownloadButton;
    private Button mFlashButton;
    private Switch mConnectionSwitch;
    private TextView mInformationTV;
    private ProgressBar mProgressBar;
    private Spinner mChannelSpinner;

    private OtaPackage mOtaPackage;

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

        Log.i(TAG, "binding update service");
        bindUpdateService();

        mHandleUpdateButton = findViewById(R.id.get_update_button);
        mDownloadButton = findViewById(R.id.download_update_button);
        mFlashButton = findViewById(R.id.flash_device_button);
        mInformationTV = findViewById(R.id.version_details);
        mProgressBar = findViewById(R.id.progressBar);
        mConnectionSwitch = findViewById(R.id.connection_type_switch);
        mChannelSpinner = findViewById(R.id.stability_channel_spinner);

        mHandleUpdateButton.setEnabled(true);
        mDownloadButton.setEnabled(false);
        mFlashButton.setEnabled(false);

        mResponseMessenger = new Messenger(new CallbackHandler());
        initChannelSpinner(getApplicationContext());
    }

    private void bindUpdateService()
    {
        Log.i(TAG, "bindUpdateService()");
        if (!mBound)
        {
            boolean bindingresult = bindService(MessengerConfig.getServiceBindIntent(), mConnection, Context.BIND_AUTO_CREATE);
            Log.i(TAG, "bindingresult: " + bindingresult);
        }
    }

    private void unbindUpdateService()
    {
        Log.i(TAG, "unbindUpdateService()");
        if (mBound)
        {
            unbindService(mConnection);
            mBound = false;
            mRequestMessenger = null;
        }
    }

    private void initChannelSpinner(Context context)
    {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(context, R.array.spinner_values, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mChannelSpinner.setAdapter(adapter);
        mChannelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                setChannelPreference(OtaChannel.parseChannelIndex(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent)
            {
                // standard channel is live
                setChannelPreference(OtaChannel.LIVE);
            }
        });
    }

    public void startOtaSearch(View v)
    {
        Log.i(TAG, "Get update clicked");

        if (!mBound)
        {
            mInformationTV.setText("Service not bound, try again");
            Log.e(TAG, "Service not bound");
            return;
        }

        try
        {
            mInformationTV.setText("Getting latest version");
            mProgressBar.setIndeterminate(true);

            Message requestMessage = OtaMetadataContract.OtaMetadataRequest.buildMessage(mResponseMessenger);
            mRequestMessenger.send(requestMessage);
        }
        catch (RemoteException e)
        {
            mInformationTV.setText("RemoteException");
            Log.e(TAG, "RemoteException", e);
        }
    }

    public void startOtaDownload(View v)
    {
        Log.i(TAG, "Download update clicked with version " + mOtaPackage);

        if (!mBound)
        {
            mInformationTV.setText("Service not bound, try again");
            Log.e(TAG, "Service not bound");
            return;
        }

        try
        {
            mInformationTV.setText("Downloading update");
            mProgressBar.setIndeterminate(true);
            Message requestMessage = OtaDownloadContract.DownloadRequest.buildMessage(mResponseMessenger, mOtaPackage);
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
            Message requestMessage = OtaInstallationContract.InstallationRequest.buildMessage(mResponseMessenger, mOtaPackage);
            mRequestMessenger.send(requestMessage);
        }
        catch (RemoteException e)
        {
            mInformationTV.setText("RemoteException");
            Log.e(TAG, "RemoteException", e);
        }
    }

    public void setConnectionTypePreference(View v)
    {
        mConnectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
        {
            if (isChecked)
            {
                Toast.makeText(getApplicationContext(), "Only wifi downloads", Toast.LENGTH_SHORT).show();
            }
            else
            {
                Toast.makeText(getApplicationContext(), "All connections allowed", Toast.LENGTH_SHORT).show();
            }
            try
            {
                Message requestMessage = OtaPreferenceContract.WifiPreferenceRequest.buildMessage(mResponseMessenger, isChecked);
                mRequestMessenger.send(requestMessage);
            }
            catch (RemoteException e)
            {
                Log.e(TAG, "Can't change preferences", e);
            }
        });
    }

    public void setChannelPreference(OtaChannel channel)
    {
        if (!mBound)
        {
            mInformationTV.setText("Service not bound, try again");
            Log.e(TAG, "Service not bound");
            return;
        }

        try
        {
            Message requestMessage = OtaPreferenceContract.ChannelPreferenceRequest.buildMessage(mResponseMessenger, channel);
            mRequestMessenger.send(requestMessage);
        }
        catch (RemoteException e)
        {
            Log.e(TAG, "Can't change preferences", e);
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
                    case GET_OTA_UP_TO_DATE:
                        mInformationTV.setText("Your OS is up to date");
                        break;

                    case GET_OTA_ERROR:
                        String retrievalError = OtaMetadataContract.MetadataErrorResponse.extractErrorMessage(payload);
                        mInformationTV.setText("Could not retrieve updates: " + retrievalError);
                        break;

                    case GET_OTA_RESULT:
                        mOtaPackage = OtaMetadataContract.OtaMetadataResponse.extractOtaList(payload);
                        Log.i(TAG, "Found version: " + mOtaPackage.getVersion());

                        String versionText = getString(R.string.image_version_message, mOtaPackage.getVersion(), mOtaPackage.getChannel(), String.valueOf(mOtaPackage.getSize()));
                        mInformationTV.setText(Html.fromHtml(versionText, Html.FROM_HTML_MODE_LEGACY));
                        mHandleUpdateButton.setEnabled(false);
                        mDownloadButton.setEnabled(true);
                        break;

                    case DOWNLOAD_OTA_ERROR:
                        String downloadError = OtaDownloadContract.DownloadErrorResponse.parseBundle(payload);
                        mInformationTV.setText("Download failed: " + downloadError);
                        break;

                    case DOWNLOAD_OTA_PROGRESS:
                        int downloadProgress = OtaDownloadContract.DownloadProgressResponse.parseBundle(payload);
                        setProgress(downloadProgress);
                        break;

                    case DOWNLOAD_OTA_SUCCESS:
                        mOtaPackage = OtaDownloadContract.DownloadSuccessResponse.parseBundle(payload);

                        // don't proceed if hashes don't match
                        if (!mOtaPackage.isValidated())
                        {
                            mInformationTV.setText("Package validation failed");
                        }
                        else
                        {
                            mInformationTV.setText("Download successful in " + mOtaPackage.getFile().getAbsolutePath());
                            mDownloadButton.setEnabled(false);
                            mFlashButton.setEnabled(true);
                        }
                        break;

                    case INSTALL_OTA_ERROR:
                        String installationError = OtaInstallationContract.InstallationErrorResponse.parseBundle(payload);
                        mInformationTV.setText("Installation failed: " + installationError);
                        break;

                    case INSTALL_OTA_PROGRESS:
                        int installProgress = OtaInstallationContract.InstallationProgressResponse.parseBundle(payload);
                        setProgress(installProgress);
                        break;

                    case INSTALL_OTA_REBOOT:
                        mInformationTV.setText("Installation complete, reboot required");
                        break;

                    default:
                        mInformationTV.setText("Unknown message " + reason.name());
                        Log.e(TAG, "Unknown message");
                }
            }
            catch (MessengerConfig.InvalidPayloadException e)
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
