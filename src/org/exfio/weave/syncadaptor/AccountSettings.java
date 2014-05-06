package org.exfio.weave.syncadaptor;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.os.Bundle;

import android.util.Log;

import at.bitfire.davdroid.resource.AccountSettingsInterface;

public class AccountSettings implements AccountSettingsInterface{
	private final static String TAG = "weave.AccountSettings";
	
	private final static int CURRENT_VERSION = 1;
	private final static String
		KEY_SETTINGS_VERSION = "version",
		KEY_USERNAME         = "user_name",
		KEY_SYNC_KEY         = "sync_key",
		KEY_AUTH_PREEMPTIVE  = "auth_preemptive",
		KEY_ADDRESSBOOK_URL  = "addressbook_url",
		KEY_ADDRESSBOOK_CTAG = "addressbook_ctag";
	
	Context context;
	AccountManager accountManager;
	Account account;
	
	
	public AccountSettings(Context context, Account account) {
		this.context = context;
		this.account = account;
		
		accountManager = AccountManager.get(context);
		
		synchronized(AccountSettings.class) {
			int version = 0;
			try {
				version = Integer.parseInt(accountManager.getUserData(account, KEY_SETTINGS_VERSION));
			} catch(NumberFormatException e) {
			}
			if (version < CURRENT_VERSION)
				update(version);
		}
	}
	
	
	public static Bundle createBundle(ServerInfo serverInfo) {
		Bundle bundle = new Bundle();
		bundle.putString(KEY_SETTINGS_VERSION, String.valueOf(CURRENT_VERSION));
		bundle.putString(KEY_ADDRESSBOOK_URL,  serverInfo.getAddressBook().getURL());
		bundle.putString(KEY_USERNAME,         serverInfo.getUserName());
		bundle.putString(KEY_AUTH_PREEMPTIVE,  Boolean.toString(serverInfo.isAuthPreemptive()));
		return bundle;
	}
	
	
	// general settings
	
	public String getUserName() {
		return accountManager.getUserData(account, KEY_USERNAME);		
	}
	
	public String getPassword() {
		return accountManager.getPassword(account);
	}
	
	public String getSyncKey() {
		return accountManager.getUserData(account, KEY_SYNC_KEY);		
	}

	public boolean getPreemptiveAuth() {
		return Boolean.parseBoolean(accountManager.getUserData(account, KEY_AUTH_PREEMPTIVE));
	}
	
	
	// address book (Weave) settings
	
	public String getAddressBookURL() {
		return accountManager.getUserData(account, KEY_ADDRESSBOOK_URL);
	}
	
	public String getAddressBookCTag() {
		return accountManager.getUserData(account, KEY_ADDRESSBOOK_CTAG);
	}
	
	public void setAddressBookCTag(String cTag) {
		accountManager.setUserData(account, KEY_ADDRESSBOOK_CTAG, cTag);
	}
	
	
	// update from previous account settings
	// NOTE: update not yet required
	
	private void update(int fromVersion) {
		Log.i(TAG, "Account settings must be updated from v" + fromVersion + " to v" + CURRENT_VERSION);
		for (int toVersion = CURRENT_VERSION; toVersion > fromVersion; toVersion--)
			update(fromVersion, toVersion);
	}
	
	private void update(int fromVersion, int toVersion) {
		Log.wtf(TAG, "Don't know how to update settings from v" + fromVersion + " to v" + toVersion);
	}	
}
