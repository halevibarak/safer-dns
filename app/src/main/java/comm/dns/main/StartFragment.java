/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package comm.dns.main;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.goldenpie.devs.pincodeview.PinCodeView;
import com.goldenpie.devs.pincodeview.core.Listeners;
import com.goldenpie.devs.pincodeview.core.LockType;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;

import comm.dns.Configuration;
import comm.dns.FileHelper;
import comm.dns.MainActivity;
import comm.dns.R;
import comm.dns.vpn.AdVpnService;
import comm.dns.vpn.Command;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;



public class StartFragment extends Fragment implements Listeners.PinEnteredListener, Listeners.PinReEnterListener, Listeners.PinMismatchListener {
    public static final int REQUEST_START_VPN = 1;
    private static final String TAG = "StartFragment";
    private PinCodeView mPinCodeView;
    private Button mStartButton;
    private Switch mSwitchOnBoot;
    private OnCompleteListener mListener;
    public StartFragment() {
    }
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.mListener = (OnCompleteListener) context;
        } catch (final ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement OnCompleteListener");
        }
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_start, container, false);
        mSwitchOnBoot = (Switch) rootView.findViewById(R.id.switch_onboot);
        mSwitchOnBoot.setEnabled(false);
        ImageView view = (ImageView) rootView.findViewById(R.id.state_image);
        if ("".equals(getContext().getSharedPreferences("state", MODE_PRIVATE).getString("pinCode", ""))) {
            AlertDialog.Builder alert = new AlertDialog.Builder(getContext());
            alert.setTitle(getString(R.string.start_title)).setMessage(getString(R.string.start_text))
                    .setNeutralButton(getString(R.string.submit_), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int d) {

                        }
                    });


            alert.show();
        }

        mStartButton = (Button) rootView.findViewById(R.id.start_button);
        mStartButton.setEnabled(false);
        mPinCodeView = (PinCodeView) rootView.findViewById(R.id.ci_drawable);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startStopService();
            }
        });
        mPinCodeView.setLockType(LockType.ENTER_PIN)
                .setPinEnteredListener(this)
                .setPinReEnterListener(this)
                .setPinMismatchListener(this);

        updateStatus(rootView, AdVpnService.vpnStatus);

        mSwitchOnBoot.setChecked(MainActivity.config.autoStart);
        mSwitchOnBoot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.config.autoStart = isChecked;
                FileHelper.writeSettings(getContext(), MainActivity.config);
            }
        });

        Switch watchDog = (Switch) rootView.findViewById(R.id.watchdog);
        watchDog.setChecked(MainActivity.config.watchDog);
        watchDog.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.config.watchDog = isChecked;
                FileHelper.writeSettings(getContext(), MainActivity.config);
            }
        });

        Switch ipV6Support = (Switch) rootView.findViewById(R.id.ipv6_support);
        ipV6Support.setChecked(MainActivity.config.ipV6Support);
        ipV6Support.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.config.ipV6Support = isChecked;
                FileHelper.writeSettings(getContext(), MainActivity.config);
            }
        });

        ExtraBar.setup(rootView.findViewById(R.id.extra_bar), "start");

        return rootView;
    }

    private boolean startStopService() {
        if (AdVpnService.vpnStatus != AdVpnService.VPN_STATUS_STOPPED) {
            Log.i(TAG, "Attempting to disconnect");

            Intent intent = new Intent(getActivity(), AdVpnService.class);
            intent.putExtra("COMMAND", Command.STOP.ordinal());
            getActivity().startService(intent);
        } else {
            checkHostsFilesAndStartService();
        }
        return true;
    }

    public static void updateStatus(View rootView, int status) {
        Context context = rootView.getContext();
        TextView stateText = (TextView) rootView.findViewById(R.id.state_textview);
        ImageView stateImage = (ImageView) rootView.findViewById(R.id.state_image);
        Button startButton = (Button) rootView.findViewById(R.id.start_button);

        if (stateImage == null || stateText == null)
            return;

        stateText.setText(rootView.getContext().getString(AdVpnService.vpnStatusToTextId(status)));
        stateImage.setContentDescription(rootView.getContext().getString(AdVpnService.vpnStatusToTextId(status)));
        stateImage.setImageAlpha(255);
        stateImage.setImageTintList(ContextCompat.getColorStateList(context, R.color.colorStateImage));
        switch (status) {
            case AdVpnService.VPN_STATUS_RECONNECTING:
            case AdVpnService.VPN_STATUS_STARTING:
            case AdVpnService.VPN_STATUS_STOPPING:
                stateImage.setImageDrawable(context.getDrawable(R.drawable.ic_settings_black_24dp));
                startButton.setText(R.string.action_stop);
                break;
            case AdVpnService.VPN_STATUS_STOPPED:
                stateImage.setImageDrawable(context.getDrawable(R.mipmap.app_icon_large));
                stateImage.setImageAlpha(32);
                stateImage.setImageTintList(null);
                startButton.setText(R.string.action_start);
                break;
            case AdVpnService.VPN_STATUS_RUNNING:
                stateImage.setImageDrawable(context.getDrawable(R.drawable.ic_verified_user_black_24dp));
                startButton.setText(R.string.action_stop);
                break;
            case AdVpnService.VPN_STATUS_RECONNECTING_NETWORK_ERROR:
                stateImage.setImageDrawable(context.getDrawable(R.drawable.ic_error_black_24dp));
                startButton.setText(R.string.action_stop);
                break;
        }
    }

    private void checkHostsFilesAndStartService() {
        if (!areHostsFilesExistant()) {
            new AlertDialog.Builder(getActivity())
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.missing_hosts_files_title)
                    .setMessage(R.string.missing_hosts_files_message)
                    .setNegativeButton(R.string.button_no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            /* Do nothing */
                        }
                    })
                    .setPositiveButton(R.string.button_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startService();
                        }
                    })
                    .show();
            return;
        }
        startService();
    }

    private void startService() {
        Log.i(TAG, "Attempting to connect");
        Intent intent = VpnService.prepare(getContext());
        if (intent != null) {
            startActivityForResult(intent, REQUEST_START_VPN);
        } else {
            onActivityResult(REQUEST_START_VPN, RESULT_OK, null);
        }
    }

    /**
     * Check if all configured hosts files exist.
     *
     * @return true if all host files exist or no host files were configured.
     */
    private boolean areHostsFilesExistant() {
        if (!MainActivity.config.hosts.enabled)
            return true;

        for (Configuration.Item item : MainActivity.config.hosts.items) {
            if (item.state != Configuration.Item.STATE_IGNORE) {
                try {
                    InputStreamReader reader = FileHelper.openItemFile(getContext(), item);
                    if (reader == null)
                        continue;

                    reader.close();
                } catch (IOException e) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult: Received result=" + resultCode + " for request=" + requestCode);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_START_VPN && resultCode == RESULT_CANCELED) {
            Toast.makeText(getContext(), R.string.could_not_configure_vpn_service, Toast.LENGTH_LONG).show();
        }
        if (requestCode == REQUEST_START_VPN && resultCode == RESULT_OK) {
            Log.d("MainActivity", "onActivityResult: Starting service");
            Intent intent = new Intent(getContext(), AdVpnService.class);
            intent.putExtra("COMMAND", Command.START.ordinal());
            intent.putExtra("NOTIFICATION_INTENT",
                    PendingIntent.getActivity(getContext(), 0,
                            new Intent(getContext(), MainActivity.class), 0));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && MainActivity.config.showNotification) {
                getContext().startForegroundService(intent);
            } else {
                getContext().startService(intent);
            }

        }
    }

    @Override
    public void onPinEntered(@Nullable String s) {
        if ("".equals(getContext().getSharedPreferences("state", MODE_PRIVATE).getString("pinCode", ""))) {
            getContext().getSharedPreferences("state", MODE_PRIVATE)
                    .edit()
                    .putString("pinCode", s)
                    .apply();
            hideKeyboard(getActivity());
        } else if (s.equals(getContext().getSharedPreferences("state", MODE_PRIVATE).getString("pinCode", ""))) {
            hideKeyboard(getActivity());
        }else {
            getActivity().finish();
        }
    }

    @Override
    public void onPinReEnterStarted() {

    }

    @Override
    public void onPinMismatch() {

    }
    public  void hideKeyboard(Activity activity) {
        mPinCodeView.setVisibility(View.GONE);
        mStartButton.setEnabled(true);
        mSwitchOnBoot.setEnabled(true);
        mListener.onPinComplete();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.

        imm.hideSoftInputFromWindow(mPinCodeView.getWindowToken(), 0);
    }

    public interface OnCompleteListener {
        void onPinComplete();

    }
}
