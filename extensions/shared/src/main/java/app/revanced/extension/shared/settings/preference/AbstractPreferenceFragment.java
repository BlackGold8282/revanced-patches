package app.revanced.extension.shared.settings.preference;

import static app.revanced.extension.shared.utils.ResourceUtils.getXmlIdentifier;
import static app.revanced.extension.shared.utils.StringRef.str;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.view.View;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.revanced.extension.shared.settings.BaseSettings;
import app.revanced.extension.shared.settings.BooleanSetting;
import app.revanced.extension.shared.settings.Setting;
import app.revanced.extension.shared.utils.Logger;
import app.revanced.extension.shared.utils.StringRef;
import app.revanced.extension.shared.utils.Utils;

@SuppressWarnings({"unused", "deprecation"})
public abstract class AbstractPreferenceFragment extends PreferenceFragment {

    /**
     * Indicates that if a preference changes,
     * to apply the change from the Setting to the UI component.
     */
    public static boolean settingImportInProgress;

    /**
     * Prevents recursive calls during preference <-> UI syncing from showing extra dialogs.
     */
    private static boolean updatingPreference;

    /**
     * Set by subclasses if Strings cannot be added as a resource.
     */
    @Nullable
    protected static String restartDialogMessage;

    /**
     * Used to prevent showing reboot dialog, if user cancels a setting user dialog.
     */
    private boolean showingUserDialogMessage;

    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (sharedPreferences, str) -> {
        try {
            if (updatingPreference) {
                Logger.printDebug(() -> "Ignoring preference change as sync is in progress");
                return;
            }
            if (str == null) {
                return;
            }
            Setting<?> setting = Setting.getSettingFromPath(str);
            if (setting == null) {
                return;
            }
            Preference pref = findPreference(str);
            if (pref == null) {
                return;
            }
            Logger.printDebug(() -> "Preference changed: " + setting.key);

            if (!settingImportInProgress && !showingUserDialogMessage) {
                if (setting.userDialogMessage != null && !prefIsSetToDefault(pref, setting)) {
                    // Do not change the setting yet, to allow preserving whatever
                    // list/text value was previously set if it needs to be reverted.
                    showSettingUserDialogConfirmation(pref, setting);
                    return;
                } else if (setting.rebootApp) {
                    showRestartDialog(getContext());
                }
            }

            updatingPreference = true;
            // Apply 'Setting <- Preference', unless during importing when it needs to be 'Setting -> Preference'.
            // Updating here can can cause a recursive call back into this same method.
            updatePreference(pref, setting, true, settingImportInProgress);
            // Update any other preference availability that may now be different.
            updateUIAvailability();
            updatingPreference = false;
        } catch (Exception ex) {
            Logger.printException(() -> "OnSharedPreferenceChangeListener failure", ex);
        }
    };

    /**
     * Initialize this instance, and do any custom behavior.
     * <p>
     * To ensure all {@link Setting} instances are correctly synced to the UI,
     * it is important that subclasses make a call or otherwise reference their Settings class bundle
     * so all app specific {@link Setting} instances are loaded before this method returns.
     */
    protected void initialize() {
        final int identifier = getXmlIdentifier("revanced_prefs");
        if (identifier == 0) return;
        addPreferencesFromResource(identifier);

        PreferenceScreen screen = getPreferenceScreen();
        Utils.sortPreferenceGroups(screen);
        Utils.setPreferenceTitlesToMultiLineIfNeeded(screen);
    }

    private void showSettingUserDialogConfirmation(Preference pref, Setting<?> setting) {
        Utils.verifyOnMainThread();

        final var context = getActivity();
        final StringRef userDialogMessage = setting.userDialogMessage;
        if (context != null && userDialogMessage != null) {
            showingUserDialogMessage = true;

            new AlertDialog.Builder(context)
                    .setTitle(android.R.string.dialog_alert_title)
                    .setMessage(userDialogMessage.toString())
                    .setPositiveButton(android.R.string.ok, (dialog, id) -> {
                        // User confirmed, save to the Setting.
                        updatePreference(pref, setting, true, false);

                        // Update availability of other preferences that may be changed.
                        updateUIAvailability();

                        if (setting.rebootApp) {
                            showRestartDialog(context);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, id) -> {
                        // Restore whatever the setting was before the change.
                        updatePreference(pref, setting, true, true);
                    })
                    .setOnDismissListener(dialog -> showingUserDialogMessage = false)
                    .setCancelable(false)
                    .show();
        }
    }

    /**
     * Updates all Preferences values and their availability using the current values in {@link Setting}.
     */
    protected void updateUIToSettingValues() {
        updatePreferenceScreen(getPreferenceScreen(), true, true);
    }

    /**
     * Updates Preferences availability only using the status of {@link Setting}.
     */
    protected void updateUIAvailability() {
        updatePreferenceScreen(getPreferenceScreen(), false, false);
    }

    /**
     * @return If the preference is currently set to the default value of the Setting.
     */
    protected boolean prefIsSetToDefault(Preference pref, Setting<?> setting) {
        Object defaultValue = setting.defaultValue;
        if (pref instanceof SwitchPreference switchPref) {
            return switchPref.isChecked() == (Boolean) defaultValue;
        }
        String defaultValueString = defaultValue.toString();
        if (pref instanceof EditTextPreference editPreference) {
            return editPreference.getText().equals(defaultValueString);
        }
        if (pref instanceof ListPreference listPref) {
            return listPref.getValue().equals(defaultValueString);
        }

        throw new IllegalStateException("Must override method to handle "
                + "preference type: " + pref.getClass());
    }


    /**
     * Syncs all UI Preferences to any {@link Setting} they represent.
     */
    private void updatePreferenceScreen(@NonNull PreferenceGroup group,
                                        boolean syncSettingValue,
                                        boolean applySettingToPreference) {
        // Alternatively this could iterate thru all Settings and check for any matching Preferences,
        // but there are many more Settings than UI preferences so it's more efficient to only check
        // the Preferences.
        for (int i = 0, prefCount = group.getPreferenceCount(); i < prefCount; i++) {
            Preference pref = group.getPreference(i);
            if (pref instanceof PreferenceGroup subGroup) {
                updatePreferenceScreen(subGroup, syncSettingValue, applySettingToPreference);
            } else if (pref.hasKey()) {
                String key = pref.getKey();
                Setting<?> setting = Setting.getSettingFromPath(key);

                if (setting != null) {
                    updatePreference(pref, setting, syncSettingValue, applySettingToPreference);
                } else if (BaseSettings.ENABLE_DEBUG_LOGGING.get() && (pref instanceof SwitchPreference
                        || pref instanceof EditTextPreference || pref instanceof ListPreference)) {
                    // Probably a typo in the patches preference declaration.
                    Logger.printException(() -> "Preference key has no setting: " + key);
                }
            }
        }
    }

    /**
     * Handles syncing a UI Preference with the {@link Setting} that backs it.
     * If needed, subclasses can override this to handle additional UI Preference types.
     *
     * @param applySettingToPreference If true, then apply {@link Setting} -> Preference.
     *                                 If false, then apply {@link Setting} <- Preference.
     */
    protected void syncSettingWithPreference(@NonNull Preference pref,
                                             @NonNull Setting<?> setting,
                                             boolean applySettingToPreference) {
        if (pref instanceof SwitchPreference switchPref) {
            BooleanSetting boolSetting = (BooleanSetting) setting;
            if (applySettingToPreference) {
                switchPref.setChecked(boolSetting.get());
            } else {
                BooleanSetting.privateSetValue(boolSetting, switchPref.isChecked());
            }
        } else if (pref instanceof EditTextPreference editPreference) {
            if (applySettingToPreference) {
                editPreference.setText(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, editPreference.getText());
            }
        } else if (pref instanceof ListPreference listPref) {
            if (applySettingToPreference) {
                listPref.setValue(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, listPref.getValue());
            }
            updateListPreferenceSummary(listPref, setting);
        } else {
            Logger.printException(() -> "Setting cannot be handled: " + pref.getClass() + ": " + pref);
        }
    }

    /**
     * Updates a UI Preference with the {@link Setting} that backs it.
     *
     * @param syncSetting              If the UI should be synced {@link Setting} <-> Preference
     * @param applySettingToPreference If true, then apply {@link Setting} -> Preference.
     *                                 If false, then apply {@link Setting} <- Preference.
     */
    private void updatePreference(@NonNull Preference pref, @NonNull Setting<?> setting,
                                  boolean syncSetting, boolean applySettingToPreference) {
        if (!syncSetting && applySettingToPreference) {
            throw new IllegalArgumentException();
        }

        if (syncSetting) {
            syncSettingWithPreference(pref, setting, applySettingToPreference);
        }

        updatePreferenceAvailability(pref, setting);
    }

    protected void updatePreferenceAvailability(@NonNull Preference pref, @NonNull Setting<?> setting) {
        pref.setEnabled(setting.isAvailable());
    }

    public static void updateListPreferenceSummary(ListPreference listPreference, Setting<?> setting) {
        String objectStringValue = setting.get().toString();
        int entryIndex = listPreference.findIndexOfValue(objectStringValue);
        if (entryIndex >= 0) {
            listPreference.setValue(objectStringValue);
            objectStringValue = listPreference.getEntries()[entryIndex].toString();
        }
        listPreference.setSummary(objectStringValue);
    }

    public static void showRestartDialog(@NonNull Context context) {
        if (restartDialogMessage == null) {
            restartDialogMessage = str("revanced_extended_restart_message");
        }

        showRestartDialog(context, restartDialogMessage);
    }

    public static void showRestartDialog(@NonNull Context context, String message) {
        showRestartDialog(context, message, 0);
    }

    public static void showRestartDialog(@NonNull Context context, String message, long delay) {
        Utils.verifyOnMainThread();

        new AlertDialog.Builder(context)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, id)
                        -> Utils.runOnMainThreadDelayed(() -> Utils.restartApp(context), delay))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @SuppressLint("ResourceType")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            PreferenceManager preferenceManager = getPreferenceManager();
            preferenceManager.setSharedPreferencesName(Setting.preferences.name);

            // Must initialize before adding change listener,
            // otherwise the syncing of Setting -> UI
            // causes a callback to the listener even though nothing changed.
            initialize();
            updateUIToSettingValues();

            preferenceManager.getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);
        } catch (Exception ex) {
            Logger.printException(() -> "onCreate() failure", ex);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final View rootView = getView();
        if (rootView == null) return;
        ListView listView = getView().findViewById(android.R.id.list);
        if (listView == null) return;
        listView.setDivider(null);
        listView.setDividerHeight(0);
    }

    @Override
    public void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener);
        super.onDestroy();
    }
}
