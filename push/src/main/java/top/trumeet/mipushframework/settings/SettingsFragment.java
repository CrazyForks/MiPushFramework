package top.trumeet.mipushframework.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.smack.ConnectionConfiguration;
import com.xiaomi.xmsf.R;
import com.xiaomi.xmsf.utils.ConfigCenter;

import moe.shizuku.preference.Preference;
import moe.shizuku.preference.PreferenceFragment;
import moe.shizuku.preference.PreferenceGroup;
import moe.shizuku.preference.SwitchPreferenceCompat;
import top.trumeet.common.Constants;
import top.trumeet.mipushframework.MainActivity;

/**
 * Created by Trumeet on 2017/8/27.
 * Main settings
 *
 * @author Trumeet
 * @see MainActivity
 */

public class SettingsFragment extends PreferenceFragment {

    private static final String TAG = SettingsFragment.class.getSimpleName();
    private static final Logger logger = XLog.tag(TAG).build();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings);
        setPreferenceOnclick("key_get_log", preference -> {
            startActivity(new Intent()
                    .setComponent(new ComponentName(Constants.SERVICE_APP_NAME,
                            Constants.SHARE_LOG_COMPONENT_NAME)));
            return true;
        });
        {
            Preference preference = getPreference("configuration_directory");
            Uri treeUri = ConfigCenter.getInstance().getConfigurationDirectory(getActivity());
            if (treeUri != null) {
                preference.setSummary(treeUri.toString());
            }
            preference.setOnPreferenceClickListener(pref -> {
                openDirectory(Uri.fromFile(
                        getActivity().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)));
                return true;
            });
        }
        {
            Preference preference = getPreference("XMPP_server");
            String host = ConfigCenter.getInstance().getXMPPServer(getActivity());
            if (!TextUtils.isEmpty(host)) {
                preference.setSummary(host);
            }
            preference.setOnPreferenceClickListener(pref -> {
                EditText editText = new EditText(getActivity());
                editText.setHint(ConnectionConfiguration.XMPP_SERVER_HOST_P + ":80");
                editText.setText(ConfigCenter.getInstance().getXMPPServer(getActivity()));
                AlertDialog.Builder build = new AlertDialog.Builder(getActivity())
                        .setView(editText)
                        .setTitle(R.string.settings_XMPP_server)
                        .setCancelable(true)
                        .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                            String newHost = String.valueOf(editText.getText());
                            ConfigCenter.getInstance().setXMPPServer(getActivity(), newHost);
                            if (TextUtils.isEmpty(newHost)) {
                                preference.setSummary(R.string.settings_XMPP_server_summary);
                            } else {
                                preference.setSummary(newHost);
                            }
                            LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(
                                    new Intent(PushConstants.ACTION_RESET_CONNECTION));
                        });
                build.create().show();
                return true;
            });
        }
    }

    private void addItem(boolean value, Preference.OnPreferenceChangeListener listener,
                         CharSequence title, CharSequence summary, PreferenceGroup parent) {
        SwitchPreferenceCompat preference = new SwitchPreferenceCompat(getContext(),
                null, moe.shizuku.preference.R.attr.switchPreferenceStyle,
                R.style.Preference_SwitchPreferenceCompat);
        preference.setOnPreferenceChangeListener(listener);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setChecked(value);
        parent.addPreference(preference);
    }

    private void setPreferenceOnclick(String key, Preference.OnPreferenceClickListener onPreferenceClickListener) {
        getPreference(key).setOnPreferenceClickListener(onPreferenceClickListener);
    }

    private Preference getPreference(String key) {
        return getPreferenceScreen().findPreference(key);
    }

    @Override
    public void onStart() {
        super.onStart();
        long time = System.currentTimeMillis();
        Log.d(TAG, "rebuild UI took: " + (System.currentTimeMillis() -
                time));
    }

    private void openDirectory(Uri uriToLoad) {
        // Choose a directory using the system's file picker.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        // Optionally, specify a URI for the directory that should be opened in
        // the system file picker when it loads.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uriToLoad);
        }

        startActivityForResult(intent, 123);
    }

    @SuppressLint("WrongConstant")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 123 && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            final int takeFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContext().getContentResolver().takePersistableUriPermission(uri, takeFlags);
                Preference preference = getPreference("configuration_directory");
                preference.setSummary(uri.toString());
                ConfigCenter.getInstance().setConfigurationDirectory(getContext(), uri);
                ConfigCenter.getInstance().loadConfigurations(getActivity());
            } catch (Throwable e) {
                logger.e("onActivityResult configuration", e);
            }
        }
    }
}
