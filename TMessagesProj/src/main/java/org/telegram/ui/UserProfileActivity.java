/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import com.andguru.telegram.messenger.R;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.LocaleController;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.android.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.android.MessagesController;
import org.telegram.android.MessagesStorage;
import org.telegram.messenger.NotificationCenter;

import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.Utilities;
import org.telegram.objects.MessageObject;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.ActionBar.BaseFragment;
import org.telegram.objects.VibrationOptions;
import org.telegram.ui.Views.IdenticonView;

import java.util.ArrayList;
import java.util.List;

public class UserProfileActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, MessagesActivity.MessagesActivityDelegate, PhotoViewer.PhotoViewerProvider {
    private ListView listView;
    private ListAdapter listAdapter;
    private int user_id;
    private int totalMediaCount = -1;
    private boolean creatingChat = false;
    private long dialog_id;
    private TLRPC.EncryptedChat currentEncryptedChat;
    private Boolean blocked = false;

    private final static int add_contact = 1;
    private final static int block_contact = 2;
    private final static int share_contact = 3;
    private final static int edit_contact = 4;
    private final static int delete_contact = 5;
    private final static int unblock_contact = 6;

    private int avatarRow;
    private int phoneSectionRow;
    private int phoneRow;
    private int settingsSectionRow;
    private int settingsTimerRow;
    private int settingsKeyRow;
    private int settingsNotificationsRow;
	private int settingsVibrationSpeedRow;
    private int settingsVibrationCountRow;
    private int sharedMediaSectionRow;
    private int sharedMediaRow;
    private int rowCount = 0;

    public UserProfileActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance().addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, MessagesController.mediaCountDidLoaded);
        NotificationCenter.getInstance().addObserver(this, MessagesController.encryptedChatCreated);
        NotificationCenter.getInstance().addObserver(this, MessagesController.encryptedChatUpdated);
        NotificationCenter.getInstance().addObserver(this, MessagesController.blockedContactsDidLoaded);
        user_id = arguments.getInt("user_id", 0);
        dialog_id = arguments.getLong("dialog_id", 0);
        if (dialog_id != 0) {
            currentEncryptedChat = MessagesController.getInstance().encryptedChats.get((int)(dialog_id >> 32));
        }
        blocked = MessagesStorage.getInstance().getBlockedContact(user_id);
        updateRowsIds();
        return MessagesController.getInstance().users.get(user_id) != null && super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.mediaCountDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.encryptedChatCreated);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.encryptedChatUpdated);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.blockedContactsDidLoaded);
    }

    private void updateRowsIds() {
        rowCount = 0;
        avatarRow = rowCount++;
        phoneSectionRow = rowCount++;
        phoneRow = rowCount++;
        settingsSectionRow = rowCount++;
        if (currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
            settingsTimerRow = rowCount++;
            settingsKeyRow = rowCount++;
        } else {
            settingsTimerRow = -1;
            settingsKeyRow = -1;
        }
        settingsNotificationsRow = rowCount++;
		settingsVibrationSpeedRow = rowCount++;
        settingsVibrationCountRow = rowCount++;
        sharedMediaSectionRow = rowCount++;
        sharedMediaRow = rowCount++;
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBarLayer.setDisplayHomeAsUpEnabled(true, R.drawable.ic_ab_back);
            actionBarLayer.setBackOverlay(R.layout.updating_state_layout);
            if (dialog_id != 0) {
                actionBarLayer.setTitle(LocaleController.getString("SecretTitle", R.string.SecretTitle));
                actionBarLayer.setTitleIcon(R.drawable.ic_lock_white, AndroidUtilities.dp(4));
            } else {
                actionBarLayer.setTitle(LocaleController.getString("ContactInfo", R.string.ContactInfo));
            }
            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == block_contact) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("AreYouSureBlockContact", R.string.AreYouSureBlockContact));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                TLRPC.User user = MessagesController.getInstance().users.get(user_id);
                                if (user == null) {
                                    return;
                                }
                                TLRPC.TL_contacts_block req = new TLRPC.TL_contacts_block();
                                req.id = MessagesController.getInputUser(user);
                                TLRPC.TL_contactBlocked blocked = new TLRPC.TL_contactBlocked();
                                blocked.user_id = user_id;
                                blocked.date = (int)(System.currentTimeMillis() / 1000);
								updateBlockedUnblocked(true);
                                ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                                    @Override
                                    public void run(TLObject response, TLRPC.TL_error error) {
									// Force update of blocked user cache
                                	ContactsController.getInstance().addBlockedContacts();
                                    }
                                });
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    }else if (id == unblock_contact) { // If we want to unlock the contact

                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("AreYouSure", R.string.AreYouSure));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                TLRPC.User user = MessagesController.getInstance().users.get(user_id);
                                if (user == null) {
                                    return;
                                }
                                TLRPC.TL_contacts_unblock req = new TLRPC.TL_contacts_unblock();
                                req.id = MessagesController.getInputUser(user);
                                TLRPC.TL_contactBlocked blocked = new TLRPC.TL_contactBlocked();
                                blocked.user_id = user_id;
                                blocked.date = (int) (System.currentTimeMillis() / 1000);
                                updateBlockedUnblocked(false);
                                ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                                    @Override
                                    public void run(TLObject response, TLRPC.TL_error error) {
                                        // Force update of blocked user cache
                                        ContactsController.getInstance().addBlockedContacts();
                                    }
                                }, true, RPCRequest.RPCRequestClassGeneric);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (id == add_contact) {
                        TLRPC.User user = MessagesController.getInstance().users.get(user_id);
                        Bundle args = new Bundle();
                        args.putInt("user_id", user.id);
                        presentFragment(new ContactAddActivity(args));
                    } else if (id == share_contact) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlySelect", true);
                        args.putBoolean("serverOnly", true);
                        MessagesActivity fragment = new MessagesActivity(args);
                        fragment.setDelegate(UserProfileActivity.this);
                        presentFragment(fragment);
                    } else if (id == edit_contact) {
                        Bundle args = new Bundle();
                        args.putInt("user_id", user_id);
                        presentFragment(new ContactAddActivity(args));
                    } else if (id == delete_contact) {
                        final TLRPC.User user = MessagesController.getInstance().users.get(user_id);
                        if (user == null || getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("AreYouSureDeleteContact", R.string.AreYouSureDeleteContact));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ArrayList<TLRPC.User> arrayList = new ArrayList<TLRPC.User>();
                                arrayList.add(user);
                                ContactsController.getInstance().deleteContact(arrayList);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    }
                }
            });

            createActionBarMenu();

            fragmentView = inflater.inflate(R.layout.user_profile_layout, container, false);
            listAdapter = new ListAdapter(getParentActivity());

            TextView textView = (TextView)fragmentView.findViewById(R.id.start_secret_button_text);
            textView.setText(LocaleController.getString("StartEncryptedChat", R.string.StartEncryptedChat));

            View startSecretButton = fragmentView.findViewById(R.id.start_secret_button);
            startSecretButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("AreYouSureSecretChat", R.string.AreYouSureSecretChat));
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            creatingChat = true;
                            MessagesController.getInstance().startSecretChat(getParentActivity(), MessagesController.getInstance().users.get(user_id));
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showAlertDialog(builder);
                }
            });
            if (dialog_id == 0) {
                startSecretButton.setVisibility(View.VISIBLE);
            } else {
                startSecretButton.setVisibility(View.GONE);
            }

            listView = (ListView)fragmentView.findViewById(R.id.listView);
            listView.setAdapter(listAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                    if (i == settingsNotificationsRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);

                        final String key;
                        if (i == settingsNotificationsRow) {
                            if (dialog_id == 0) {
                                key = "notify2_" + user_id;
                            } else {
                                key = "notify2_" + dialog_id;
                            }
                        } else {
                            key = null;
                        }
                        int currentValue = preferences.getInt(key, 0);

                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("Notifications", R.string.Notifications));
                        builder.setSingleChoiceItems(new CharSequence[]{
                                LocaleController.getString("Default", R.string.Default),
                                LocaleController.getString("Enabled", R.string.Enabled),
                                LocaleController.getString("Disabled", R.string.Disabled)
                        }, currentValue, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences.Editor editor = preferences.edit();

                                if (which != 0)
                                    editor.putInt(key, which);
                                else
                                    editor.remove(key);
                                editor.commit();
                                if (listView != null) {
                                    listView.invalidateViews();
                                }

                                dialog.dismiss();
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if(i == settingsVibrationSpeedRow) {
                        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        final String key;
                        if (dialog_id == 0) {
                            key = "VibrationSpeed_" + user_id;
                        } else {
                            key = "VibrationSpeed_" + dialog_id;
                        }

                        VibrationOptions.VibrationSpeed[] vibrationSpeeds = VibrationOptions.VibrationSpeed.values();
                        String speeds[] = new String[vibrationSpeeds.length + 1];
                        speeds[0] = LocaleController.getString("Default", R.string.Default);
                        for(int j = 0, vl = vibrationSpeeds.length; j < vl; j++) {
                            VibrationOptions.VibrationSpeed speedVal = vibrationSpeeds[j];
                            speeds[j + 1] = LocaleController.getString(speedVal.getLocaleKey(), speedVal.getResourceId());
                        }
                        int currentSpeedIndex = 0;

                        int storedValue = preferences.getInt(key, -1);
                        if(storedValue != -1) {
                            VibrationOptions.VibrationSpeed currentSpeed = VibrationOptions.VibrationSpeed.fromValue(storedValue);
                            currentSpeedIndex = currentSpeed.getValue() + 1; // index 0 is used to store the "Default" string
                        }

                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity())
                            .setTitle(LocaleController.getString("VibrateSpeedTitle", R.string.VibrateSpeedTitle))
                            .setSingleChoiceItems(speeds, currentSpeedIndex, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    SharedPreferences.Editor editor = preferences.edit();
                                    if(which != 0) {
                                        which--;
                                        VibrationOptions.VibrationSpeed selectedSpeed = VibrationOptions.VibrationSpeed.fromValue(which);

                                        editor.putInt(key, selectedSpeed.getValue());
                                    }
                                    else
                                        editor.remove(key);

                                    editor.commit();
                                    listView.invalidateViews();

                                    dialog.dismiss();
                                }
                            })
                            .setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        builder.show().setCanceledOnTouchOutside(true);
                    } else if(i == settingsVibrationCountRow) {
                        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        final String key;
                        if (dialog_id == 0) {
                            key = "VibrationCount_" + user_id;
                        } else {
                            key = "VibrationCount_" + dialog_id;
                        }

                        String counts[] = new String[11];
                        counts[0] = LocaleController.getString("Default", R.string.Default);
                        for(int j = 1, vl = counts.length; j < vl; j++)
                            counts[j] = String.valueOf(j);

                        int count = preferences.getInt(key, 0);

                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity())
                            .setTitle(LocaleController.getString("VibrateCountTitle", R.string.VibrateCountTitle))
                            .setSingleChoiceItems(counts, count, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    SharedPreferences.Editor editor = preferences.edit();
                                    if(which != 0) {
                                        editor.putInt(key, which);
                                    }
                                    else
                                        editor.remove(key);

                                    editor.commit();
                                    listView.invalidateViews();

                                    dialog.dismiss();
                                }
                            }).setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (i == sharedMediaRow) {
                        Bundle args = new Bundle();
                        if (dialog_id != 0) {
                            args.putLong("dialog_id", dialog_id);
                        } else {
                            args.putLong("dialog_id", user_id);
                        }
                        presentFragment(new MediaActivity(args));
                    } else if (i == settingsKeyRow) {
                        Bundle args = new Bundle();
                        args.putInt("chat_id", (int)(dialog_id >> 32));
                        presentFragment(new IdenticonActivity(args));
                    } else if (i == settingsTimerRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("MessageLifetime", R.string.MessageLifetime));
                        builder.setItems(new CharSequence[]{
                                LocaleController.getString("ShortMessageLifetimeForever", R.string.ShortMessageLifetimeForever),
                                LocaleController.getString("ShortMessageLifetime2s", R.string.ShortMessageLifetime2s),
                                LocaleController.getString("ShortMessageLifetime5s", R.string.ShortMessageLifetime5s),
                                LocaleController.getString("ShortMessageLifetime1m", R.string.ShortMessageLifetime1m),
                                LocaleController.getString("ShortMessageLifetime1h", R.string.ShortMessageLifetime1h),
                                LocaleController.getString("ShortMessageLifetime1d", R.string.ShortMessageLifetime1d),
                                LocaleController.getString("ShortMessageLifetime1w", R.string.ShortMessageLifetime1w)

                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                int oldValue = currentEncryptedChat.ttl;
                                if (which == 0) {
                                    currentEncryptedChat.ttl = 0;
                                } else if (which == 1) {
                                    currentEncryptedChat.ttl = 2;
                                } else if (which == 2) {
                                    currentEncryptedChat.ttl = 5;
                                } else if (which == 3) {
                                    currentEncryptedChat.ttl = 60;
                                } else if (which == 4) {
                                    currentEncryptedChat.ttl = 60 * 60;
                                } else if (which == 5) {
                                    currentEncryptedChat.ttl = 60 * 60 * 24;
                                } else if (which == 6) {
                                    currentEncryptedChat.ttl = 60 * 60 * 24 * 7;
                                }
                                if (oldValue != currentEncryptedChat.ttl) {
                                    if (listView != null) {
                                        listView.invalidateViews();
                                    }
                                    MessagesController.getInstance().sendTTLMessage(currentEncryptedChat);
                                    MessagesStorage.getInstance().updateEncryptedChat(currentEncryptedChat);
                                }
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (i == settingsNotificationsRow) {
                        Bundle args = new Bundle();
                        args.putLong("dialog_id", dialog_id == 0 ? user_id : dialog_id);
                        presentFragment(new ProfileNotificationsActivity(args));
                    }
                }
            });
            if (dialog_id != 0) {
                MessagesController.getInstance().getMediaCount(dialog_id, classGuid, true);
            } else {
                MessagesController.getInstance().getMediaCount(user_id, classGuid, true);
            }
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    /**
     * Modify the option block/unblock contact and recreate the Menu to fix this modification
     * @param newState New state of current contact
     */
    public void updateBlockedUnblocked(boolean newState) {
        blocked = newState;
        createActionBarMenu();
    }
    

    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.updateInterfaces) {
            int mask = (Integer)args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                if (listView != null) {
                    listView.invalidateViews();
                }
            }
        } else if (id == MessagesController.contactsDidLoaded) {
            createActionBarMenu();
        } else if (id == MessagesController.mediaCountDidLoaded) {
            long uid = (Long)args[0];
            if (uid > 0 && user_id == uid && dialog_id == 0 || dialog_id != 0 && dialog_id == uid) {
                totalMediaCount = (Integer)args[1];
                if (listView != null) {
                    listView.invalidateViews();
                }
            }
        } else if (id == MessagesController.encryptedChatCreated) {
            if (creatingChat) {
                NotificationCenter.getInstance().postNotificationName(MessagesController.closeChats);
                TLRPC.EncryptedChat encryptedChat = (TLRPC.EncryptedChat)args[0];
                Bundle args2 = new Bundle();
                args2.putInt("enc_id", encryptedChat.id);
                presentFragment(new ChatActivity(args2), true);
            }
        } else if (id == MessagesController.encryptedChatUpdated) {
            TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat)args[0];
            if (currentEncryptedChat != null && chat.id == currentEncryptedChat.id) {
                currentEncryptedChat = chat;
                updateRowsIds();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == MessagesController.blockedContactsDidLoaded) {
            blocked = MessagesStorage.getInstance().getBlockedContact(user_id);
            createActionBarMenu();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        if (fileLocation == null) {
            return null;
        }
        TLRPC.User user = MessagesController.getInstance().users.get(user_id);
        if (user != null && user.photo != null && user.photo.photo_big != null) {
            TLRPC.FileLocation photoBig = user.photo.photo_big;
            if (photoBig.local_id == fileLocation.local_id && photoBig.volume_id == fileLocation.volume_id && photoBig.dc_id == fileLocation.dc_id) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = listView.getChildAt(a);
                    BackupImageView avatarImage = (BackupImageView)view.findViewById(R.id.settings_avatar_image);
                    if (avatarImage != null) {
                        int coords[] = new int[2];
                        avatarImage.getLocationInWindow(coords);
                        PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                        object.viewX = coords[0];
                        object.viewY = coords[1] - AndroidUtilities.statusBarHeight;
                        object.parentView = listView;
                        object.imageReceiver = avatarImage.imageReceiver;
                        object.user_id = user_id;
                        object.thumb = object.imageReceiver.getBitmap();
                        object.size = -1;
                        return object;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) { }

    @Override
    public void willHidePhotoViewer() { }

    @Override
    public boolean isPhotoChecked(int index) { return false; }

    @Override
    public void setPhotoChecked(int index) { }

    @Override
    public void cancelButtonPressed() { }

    @Override
    public void sendButtonPressed(int index) { }

    @Override
    public int getSelectedCount() { return 0; }

    private void createActionBarMenu() {
        final ActionBarMenu menu = actionBarLayer.createMenu();
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                menu.clearItems();
                if (ContactsController.getInstance().contactsDict.get(user_id) == null) {
                    TLRPC.User user = MessagesController.getInstance().users.get(user_id);
                    if (user == null) {
                        return;
                    }
                    ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
                    if (user.phone != null && user.phone.length() != 0) {
                        item.addSubItem(add_contact, LocaleController.getString("AddContact", R.string.AddContact), 0);
                        if (blocked) // If the user is blocked, show "Unblock"
                            item.addSubItem(unblock_contact, LocaleController.getString("Unblock", R.string.Unblock), 0);
                        else // Otherwise show "Block"
                            item.addSubItem(block_contact, LocaleController.getString("BlockContact", R.string.BlockContact), 0);
                    } else {
                        if (blocked) // If the user is blocked, show "Unblock"
                            item.addSubItem(unblock_contact, LocaleController.getString("Unblock", R.string.Unblock), 0);
                        else // Otherwise show "Block"
                            item.addSubItem(block_contact, LocaleController.getString("BlockContact", R.string.BlockContact), 0);
                    }
                } else {
                    ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
                    item.addSubItem(share_contact, LocaleController.getString("ShareContact", R.string.ShareContact), 0);
                    if (blocked) // If the user is blocked, show "Unblock"
                        item.addSubItem(unblock_contact, LocaleController.getString("Unblock", R.string.Unblock), 0);
                    else // Otherwise show "Block"
                        item.addSubItem(block_contact, LocaleController.getString("BlockContact", R.string.BlockContact), 0);
                    item.addSubItem(edit_contact, LocaleController.getString("EditContact", R.string.EditContact), 0);
                    item.addSubItem(delete_contact, LocaleController.getString("DeleteContact", R.string.DeleteContact), 0);
                }
            }
        });

    }

    @Override
    public void didSelectDialog(MessagesActivity messageFragment, long dialog_id, boolean param) {
        if (dialog_id != 0) {
            Bundle args = new Bundle();
            args.putBoolean("scrollToTopOnResume", true);
            NotificationCenter.getInstance().postNotificationName(MessagesController.closeChats);
            int lower_part = (int)dialog_id;
            if (lower_part != 0) {
                if (lower_part > 0) {
                    args.putInt("user_id", lower_part);
                } else if (lower_part < 0) {
                    args.putInt("chat_id", -lower_part);
                }
            } else {
                args.putInt("enc_id", (int)(dialog_id >> 32));
            }
            presentFragment(new ChatActivity(args), true);
            messageFragment.removeSelfFromStack();
            removeSelfFromStack();
            TLRPC.User user = MessagesController.getInstance().users.get(user_id);
            MessagesController.getInstance().sendMessage(user, dialog_id);
        }
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return i == phoneRow || i == settingsTimerRow || i == settingsKeyRow || i == settingsNotificationsRow || i == sharedMediaRow || i == settingsVibrationSpeedRow || i == settingsVibrationCountRow;
        }

        @Override
        public int getCount() {
            return rowCount;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int type = getItemViewType(i);
            if (type == 0) {
                BackupImageView avatarImage;
                TextView onlineText;
                TLRPC.User user = MessagesController.getInstance().users.get(user_id);
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.user_profile_avatar_layout, viewGroup, false);

                    onlineText = (TextView)view.findViewById(R.id.settings_online);
                    avatarImage = (BackupImageView)view.findViewById(R.id.settings_avatar_image);
                    avatarImage.processDetach = false;
                    avatarImage.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            TLRPC.User user = MessagesController.getInstance().users.get(user_id);
                            if (user.photo != null && user.photo.photo_big != null) {
                                PhotoViewer.getInstance().setParentActivity(getParentActivity());
                                PhotoViewer.getInstance().openPhoto(user.photo.photo_big, UserProfileActivity.this);
                            }
                        }
                    });
                } else {
                    avatarImage = (BackupImageView)view.findViewById(R.id.settings_avatar_image);
                    onlineText = (TextView)view.findViewById(R.id.settings_online);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_name);
                Typeface typeface = AndroidUtilities.getTypeface("fonts/rmedium.ttf");
                textView.setTypeface(typeface);

                textView.setText(Utilities.formatName(user.first_name, user.last_name));
                onlineText.setText(LocaleController.formatUserStatus(user));

                TLRPC.FileLocation photo = null;
                TLRPC.FileLocation photoBig = null;
                if (user.photo != null) {
                    photo = user.photo.photo_small;
                    photoBig = user.photo.photo_big;
                }
                avatarImage.setImage(photo, "50_50", Utilities.getUserAvatarForId(user.id));
                avatarImage.imageReceiver.setVisible(!PhotoViewer.getInstance().isShowingImage(photoBig), false);
                return view;
            } else if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_section_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_section_text);
                if (i == phoneSectionRow) {
                    textView.setText(LocaleController.getString("PHONE", R.string.PHONE));
                } else if (i == settingsSectionRow) {
                    textView.setText(LocaleController.getString("SETTINGS", R.string.SETTINGS));
                } else if (i == sharedMediaSectionRow) {
                    textView.setText(LocaleController.getString("SHAREDMEDIA", R.string.SHAREDMEDIA));
                }
            } else if (type == 2) {
                final TLRPC.User user = MessagesController.getInstance().users.get(user_id);
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.user_profile_phone_layout, viewGroup, false);
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (user.phone == null || user.phone.length() == 0 || getParentActivity() == null) {
                                return;
                            }

                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                            builder.setItems(new CharSequence[] {LocaleController.getString("Copy", R.string.Copy)}, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    if (i == 0) {
                                        int sdk = android.os.Build.VERSION.SDK_INT;
                                        if(sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
                                            android.text.ClipboardManager clipboard = (android.text.ClipboardManager)ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                            clipboard.setText(user.phone);
                                        } else {
                                            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                            android.content.ClipData clip = android.content.ClipData.newPlainText("label", user.phone);
                                            clipboard.setPrimaryClip(clip);
                                        }
                                    }
                                }
                            });
                            showAlertDialog(builder);
                        }
                    });
                    ImageButton button = (ImageButton)view.findViewById(R.id.settings_edit_name);
                    button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            TLRPC.User user = MessagesController.getInstance().users.get(user_id);
                            if (user == null || user instanceof TLRPC.TL_userEmpty) {
                                return;
                            }
                            NotificationCenter.getInstance().postNotificationName(MessagesController.closeChats);
                            Bundle args = new Bundle();
                            args.putInt("user_id", user_id);
                            presentFragment(new ChatActivity(args), true);
                        }
                    });
                    button = (ImageButton)view.findViewById(R.id.settings_call_phone);
                    button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            try {
                                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+" + user.phone));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                getParentActivity().startActivity(intent);
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                        }
                    });
                }
                ImageButton button = (ImageButton)view.findViewById(R.id.settings_call_phone);
                button.setVisibility(user.phone == null || user.phone.length() == 0 ? View.GONE : View.VISIBLE);

                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                TextView detailTextView = (TextView)view.findViewById(R.id.settings_row_text_detail);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == phoneRow) {
                    if (user.phone != null && user.phone.length() != 0) {
                        textView.setText(PhoneFormat.getInstance().format("+" + user.phone));
                    } else {
                        textView.setText("Unknown");
                    }
                    divider.setVisibility(View.INVISIBLE);
                    detailTextView.setText(LocaleController.getString("PhoneMobile", R.string.PhoneMobile));
                }
            } else if (type == 3) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.user_profile_leftright_row_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                TextView detailTextView = (TextView)view.findViewById(R.id.settings_row_text_detail);

                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == sharedMediaRow) {
                    textView.setText(LocaleController.getString("SharedMedia", R.string.SharedMedia));
                    if (totalMediaCount == -1) {
                        detailTextView.setText(LocaleController.getString("Loading", R.string.Loading));
                    } else {
                        detailTextView.setText(String.format("%d", totalMediaCount));
                    }
                    divider.setVisibility(View.INVISIBLE);
                } else if (i == settingsTimerRow) {
                    TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().encryptedChats.get((int)(dialog_id >> 32));
                    textView.setText(LocaleController.getString("MessageLifetime", R.string.MessageLifetime));
                    divider.setVisibility(View.VISIBLE);
                    if (encryptedChat.ttl == 0) {
                        detailTextView.setText(LocaleController.getString("ShortMessageLifetimeForever", R.string.ShortMessageLifetimeForever));
                    } else if (encryptedChat.ttl == 2) {
                        detailTextView.setText(LocaleController.getString("ShortMessageLifetime2s", R.string.ShortMessageLifetime2s));
                    } else if (encryptedChat.ttl == 5) {
                        detailTextView.setText(LocaleController.getString("ShortMessageLifetime5s", R.string.ShortMessageLifetime5s));
                    } else if (encryptedChat.ttl == 60) {
                        detailTextView.setText(LocaleController.getString("ShortMessageLifetime1m", R.string.ShortMessageLifetime1m));
                    } else if (encryptedChat.ttl == 60 * 60) {
                        detailTextView.setText(LocaleController.getString("ShortMessageLifetime1h", R.string.ShortMessageLifetime1h));
                    } else if (encryptedChat.ttl == 60 * 60 * 24) {
                        detailTextView.setText(LocaleController.getString("ShortMessageLifetime1d", R.string.ShortMessageLifetime1d));
                    } else if (encryptedChat.ttl == 60 * 60 * 24 * 7) {
                        detailTextView.setText(LocaleController.getString("ShortMessageLifetime1w", R.string.ShortMessageLifetime1w));
                    } else {
                        detailTextView.setText(String.format("%d", encryptedChat.ttl));
                    }
                } else if (i == settingsNotificationsRow) {
                    textView.setText(LocaleController.getString("Notifications", R.string.Notifications));
                    divider.setVisibility(View.VISIBLE);
                    SharedPreferences preferences = mContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    String key;
                    if (dialog_id == 0) {
                        key = "notify2_" + user_id;
                    } else {
                        key = "notify2_" + dialog_id;
                    }
                    int value = preferences.getInt(key, 0);
                    if (value == 0) {
                        detailTextView.setText(LocaleController.getString("Default", R.string.Default));
                    } else if (value == 1) {
                        detailTextView.setText(LocaleController.getString("Enabled", R.string.Enabled));
                    } else if (value == 2) {
                        detailTextView.setText(LocaleController.getString("Disabled", R.string.Disabled));
                    }
                } else if(i == settingsVibrationSpeedRow) {
                    String key;
                    if (dialog_id == 0) {
                        key = "VibrationSpeed_" + user_id;
                    } else {
                        key = "VibrationSpeed_" + dialog_id;
                    }

                    textView.setText(LocaleController.getString("VibrateSpeed", R.string.VibrateSpeed));
                    divider.setVisibility(View.VISIBLE);
                    SharedPreferences preferences = mContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    int storedValue = preferences.getInt(key, -1);
                    if(storedValue != -1) {
                        VibrationOptions.VibrationSpeed speed = VibrationOptions.VibrationSpeed.fromValue(storedValue);
                        detailTextView.setText(LocaleController.getString(speed.getLocaleKey(), speed.getResourceId()));
                    }
                    else
                        detailTextView.setText(LocaleController.getString("Default", R.string.Default));

                } else if(i == settingsVibrationCountRow) {
                    String key;
                    if (dialog_id == 0) {
                        key = "VibrationCount_" + user_id;
                    } else {
                        key = "VibrationCount_" + dialog_id;
                    }

                    textView.setText(LocaleController.getString("VibrateCount", R.string.VibrateCount));
                    divider.setVisibility(View.VISIBLE);
                    SharedPreferences preferences = mContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    int count = preferences.getInt(key, -1);
                    if(count != -1)
                        detailTextView.setText(String.valueOf(count));
                    else
                        detailTextView.setText(LocaleController.getString("Default", R.string.Default));

                }
            } else if (type == 4) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.user_profile_identicon_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                View divider = view.findViewById(R.id.settings_row_divider);
                divider.setVisibility(View.VISIBLE);
                IdenticonView identiconView = (IdenticonView)view.findViewById(R.id.identicon_view);
                TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().encryptedChats.get((int)(dialog_id >> 32));
                identiconView.setBytes(encryptedChat.auth_key);
                textView.setText(LocaleController.getString("EncryptionKey", R.string.EncryptionKey));
            } else if (type == 5) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_row_button_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == settingsNotificationsRow) {
                    textView.setText(LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds));
                    divider.setVisibility(View.INVISIBLE);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == avatarRow) {
                return 0;
            } else if (i == phoneSectionRow || i == settingsSectionRow || i == sharedMediaSectionRow) {
                return 1;
            } else if (i == phoneRow) {
                return 2;
            } else if (i == sharedMediaRow || i == settingsTimerRow || i == settingsNotificationsRow || i == settingsVibrationSpeedRow || i == settingsVibrationCountRow) {
                return 3;
            } else if (i == settingsKeyRow) {
                return 4;
            } else if (i == settingsNotificationsRow) {
                return 5;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 6;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
