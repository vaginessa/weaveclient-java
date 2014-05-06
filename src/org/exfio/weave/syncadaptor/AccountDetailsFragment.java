/*******************************************************************************
 * Copyright (c) 2014 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Richard Hirner (bitfire web engineering) - initial API and implementation
 ******************************************************************************/
package org.exfio.weave.syncadaptor;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Fragment;
import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import at.bitfire.davdroid.R;

public class AccountDetailsFragment extends Fragment implements TextWatcher {
	public static final String KEY_SERVER_INFO = "server_info";
	
	ServerInfo serverInfo;
	
	EditText editAccountName;
	
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.exfio_account_details, container, false);
		
		serverInfo = (ServerInfo)getArguments().getSerializable(KEY_SERVER_INFO);
		
		editAccountName = (EditText)v.findViewById(R.id.account_name);
		editAccountName.addTextChangedListener(this);
		
		setHasOptionsMenu(true);
		return v;
	}
	
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    inflater.inflate(R.menu.account_details, menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.add_account:
			addAccount();
			break;
		default:
			return false;
		}
		return true;
	}


	// actions
	
	void addAccount() {
		ServerInfo serverInfo = (ServerInfo)getArguments().getSerializable(KEY_SERVER_INFO);
		try {
			String accountName = editAccountName.getText().toString();
			
			AccountManager accountManager = AccountManager.get(getActivity());
			Account account = new Account(accountName, org.exfio.weave.Constants.ACCOUNT_TYPE);
			Bundle userData = AccountSettings.createBundle(serverInfo);
			
			if (serverInfo.getAddressBook().isEnabled()) {
				ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
				ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
			} else {
				ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 0);
			}
			
			//TODO - make synckey concatenation more reliable
			if (accountManager.addAccountExplicitly(account, serverInfo.getPassword() + "SYNCKEY:" + serverInfo.getSyncKey(), userData)) {
				ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 0);
				
				getActivity().finish();				
			} else {
				Toast.makeText(getActivity(), "Couldn't create account (account with this name already existing?)", Toast.LENGTH_LONG).show();
			}
			
		} catch (Exception e) {
		}
	}

	
	// input validation
	
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		boolean ok = false;
		ok = editAccountName.getText().length() > 0;
		MenuItem item = menu.findItem(R.id.add_account);
		item.setEnabled(ok);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		getActivity().invalidateOptionsMenu();
	}

	@Override
	public void afterTextChanged(Editable s) {
	}
}
