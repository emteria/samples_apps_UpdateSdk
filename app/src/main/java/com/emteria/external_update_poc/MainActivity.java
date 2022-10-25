package com.emteria.external_update_poc;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Build;
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

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.emteria.update.contract.ChannelPreference;
import com.emteria.update.contract.ConnectionType;
import com.emteria.update.contract.DownloadUpdate;
import com.emteria.update.contract.FlashUpdate;
import com.emteria.update.contract.GetVersion;
import com.emteria.update.contract.ImageVersion;
import com.emteria.update.contract.MessengerConfig;


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

    private ImageVersion mFoundVersion;
    private String mDownloadedPackagePath;

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

    @RequiresApi(api = Build.VERSION_CODES.M)
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

        mResponseMessenger = new Messenger(new CallbackHandler(this));
        initChannelSpinner(getApplicationContext());
        mChannelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                setChannelPreference(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent)
            {
                // standard channel is live
                setChannelPreference(0);
            }
        });
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
    }

    public void startGettingVersionOnClick(View v)
    {
        Log.i(TAG, "Get update clicked");
        if (mBound)
        {
            Log.i(TAG, "is bound");
            try
            {
                mInformationTV.setText("Getting latest version");
                mProgressBar.setIndeterminate(true);
                Log.i(TAG, "create message");
                Message requestMessage = GetVersion.Request.buildMessage(mResponseMessenger, null);
                Log.i(TAG, "send message");
                mRequestMessenger.send(requestMessage);
            }
            catch (RemoteException e)
            {
                mInformationTV.setText("RemoteException");
                Log.e(TAG, "RemoteException", e);
            }
        }
        else
        {
            mInformationTV.setText("Service not bound, try again");
            Log.e(TAG, "Service not bound");
        }
    }

    public void startDownloadOnClick(View v)
    {
        Log.i(TAG, "Download update clicked with version " + mFoundVersion);
        if (mBound)
        {
            try
            {
                mInformationTV.setText("Downloading update");
                mProgressBar.setIndeterminate(true);
                Message requestMessage = DownloadUpdate.Request.buildMessage(mResponseMessenger, mFoundVersion);
                mRequestMessenger.send(requestMessage);
            }
            catch (RemoteException e)
            {
                mInformationTV.setText("RemoteException");
                Log.e(TAG, "RemoteException", e);
            }
        }
        else
        {
            mInformationTV.setText("Service not bound, try again");
            Log.e(TAG, "Service not bound");
        }
    }

    public void startFlashingOnClick(View v)
    {
        Log.i(TAG, "Install update clicked with version " + mDownloadedPackagePath);
        if (mBound)
        {
            try
            {
                mInformationTV.setText("Flashing update, the device would normally reboot to update.");
//                mProgressBar.setIndeterminate(true);
                Message requestMessage = FlashUpdate.Request.buildMessage(mResponseMessenger, mDownloadedPackagePath);
                mRequestMessenger.send(requestMessage);
            }
            catch (RemoteException e)
            {
                mInformationTV.setText("RemoteException");
                Log.e(TAG, "RemoteException", e);
            }
        }
        else
        {
            mInformationTV.setText("Service not bound, try again");
            Log.e(TAG, "Service not bound");
        }
    }

    public void setConnectionTypePreference(View v)
    {
        mConnectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
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
                Message requestMessage = ConnectionType.Request.buildMessage(mResponseMessenger, isChecked);
                mRequestMessenger.send(requestMessage);
            }
            catch (RemoteException e)
            {
                e.printStackTrace();
            }
        });
    }

    public void setChannelPreference(int channel)
    {
        if (mBound)
        {
            try
            {
                Message requestMessage = ChannelPreference.Request.buildMessage(mResponseMessenger, channel);
                mRequestMessenger.send(requestMessage);
            }
            catch (RemoteException e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            mInformationTV.setText("Service not bound, try again");
            Log.e(TAG, "Service not bound");
        }
    }

    @Override
    protected void onDestroy()
    {
        Log.i(TAG, "onDestroy()");
        super.onDestroy();
        unbindUpdateService();
    }

    private static class CallbackHandler extends Handler
    {
        private static final String TAG = "ExternalUpdateSample - CallbackHandler";
        private final MainActivity activity;

        CallbackHandler(MainActivity activity) {this.activity = activity;}

        @Override
        public void handleMessage(Message msg)
        {
            Log.i(TAG, "handleMessage()");
            int index = msg.what;
            MessengerConfig.ResponseReason[] values = MessengerConfig.ResponseReason.values();
            if (index < 0 || index >= values.length)
            {
                Log.e(TAG, "Handler received unknown message. Message processing aborted.");
            }
            MessengerConfig.ResponseReason reason = values[index];
            Log.i(TAG, "Received message " + reason);

            activity.mProgressBar.setIndeterminate(false);
            try
            {
                switch (reason)
                {
                    case GET_VERSION_UPDATE_FOUND:
                        ImageVersion version = GetVersion.ResponseUpdateFound.parseBundle(msg.getData());
                        Log.i(TAG, version.getVersion());

                        String text = activity.getString(R.string.image_version_message, version.getVersion(), version.getChannel(), String.valueOf(version.getSize()));
                        activity.mInformationTV.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY));
                        activity.mHandleUpdateButton.setEnabled(false);
                        activity.mDownloadButton.setEnabled(true);

                        // remember the version for download
                        activity.mFoundVersion = version;
                        break;
                    case GET_VERSION_UP_TO_DATE:
                        activity.mInformationTV.setText("Your system is up to date");
                        break;
                    case GET_VERSION_ERROR:
                        activity.mInformationTV.setText("Could not access requested update version");
                        break;

                    case DOWNLOAD_UPDATE_SUCCESS:
                        DownloadUpdate.ResponseDownloadSuccess.Result result = DownloadUpdate.ResponseDownloadSuccess.parseBundle(msg.getData());

                        // don't proceed if hashes don't match
                        if (result.isValidated)
                        {
                            activity.mInformationTV.setText("Download successful for path\n" + result.packagePath);
                            activity.mDownloadButton.setEnabled(false);
                            activity.mFlashButton.setEnabled(true);

                            // remember the OTA file path for flashing
                            activity.mDownloadedPackagePath = result.packagePath;
                        }
                        else
                        {
                            activity.mInformationTV.setText("Package hash does not match");
                        }
                        break;
                    case DOWNLOAD_UPDATE_ERROR:
                        String message = DownloadUpdate.ResponseDownloadError.parseBundle(msg.getData());
                        activity.mInformationTV.setText("Download failed\n" + message);
                        break;
                    case DOWNLOAD_UPDATE_PROGRESS_CHANGED:
                        int progress = DownloadUpdate.ProgressChanged.parseBundle(msg.getData());
                        setProgress(progress);
                        break;

                    case FLASH_UPDATE_ERROR:
                        activity.mInformationTV.setText("Flashing was not successful");
                        break;
                    case FLASH_UPDATE_PROGRESS_CHANGED:
                        // similar
                        break;

                    default:
                        activity.mInformationTV.setText("Could not access requested update version");
                        Log.e(TAG, "Unknown message");
                }
            }
            catch (MessengerConfig.InvalidPayloadException e)
            {
                Log.e(TAG, "Invalid message payload " + e.getMessage());
                activity.mInformationTV.setText("Could not access requested update version");
            }
        }

        private void setProgress(int progress)
        {
            boolean indeterminate = (progress == -1);
            if (indeterminate) { progress = 0; }

            if (activity.mProgressBar != null)
            {
                activity.mProgressBar.setIndeterminate(indeterminate);
                activity.mProgressBar.setProgress(progress);
            }
        }
    }
}
