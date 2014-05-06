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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

import android.app.DialogFragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Loader;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;
import at.bitfire.davdroid.R;

import ch.boye.httpclientandroidlib.HttpException;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;

public class QueryServerDialogFragment extends DialogFragment implements LoaderCallbacks<ServerInfo> {
	private static final String TAG = "exfio.QueryServerDialogFragment";
	public static final String
		EXTRA_BASE_URL        = "base_uri",
		EXTRA_USER_NAME       = "user_name",
		EXTRA_PASSWORD        = "password",
		EXTRA_SYNC_KEY        = "sync_key",
		EXTRA_AUTH_PREEMPTIVE = "auth_preemptive";
	
	ProgressBar progressBar;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Holo_Light_Dialog);
		setCancelable(false);

		Loader<ServerInfo> loader = getLoaderManager().initLoader(0, getArguments(), this);
		if (savedInstanceState == null)		// http://code.google.com/p/android/issues/detail?id=14944
			loader.forceLoad();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.query_server, container, false);
		return v;
	}

	@Override
	public Loader<ServerInfo> onCreateLoader(int id, Bundle args) {
		Log.i(TAG, "onCreateLoader");
		return new ServerInfoLoader(getActivity(), args);
	}

	@Override
	public void onLoadFinished(Loader<ServerInfo> loader, ServerInfo serverInfo) {
		if (serverInfo.getErrorMessage() != null) {
			Toast.makeText(getActivity(), serverInfo.getErrorMessage(), Toast.LENGTH_LONG).show();
		}
		
		getDialog().dismiss();
	}

	@Override
	public void onLoaderReset(Loader<ServerInfo> arg0) {
	}
	
	
	static class ServerInfoLoader extends AsyncTaskLoader<ServerInfo> {
		private static final String TAG = "exfio.ServerInfoLoader";
		Bundle args;
		
		public ServerInfoLoader(Context context, Bundle args) {
			super(context);
			this.args = args;
		}

		@Override
		public ServerInfo loadInBackground() {
			ServerInfo serverInfo = new ServerInfo(
				args.getString(EXTRA_BASE_URL),
				args.getString(EXTRA_USER_NAME),
				args.getString(EXTRA_PASSWORD),
				args.getString(EXTRA_SYNC_KEY),
				args.getBoolean(EXTRA_AUTH_PREEMPTIVE)
			);
			
			try {
				// TODO - query weave sync server
				
				// (1/4) detect capabilities
				//WebDavResource base = new WebDavResource(httpClient, new URI(serverInfo.getProvidedURL()), serverInfo.getUserName(),
				//		serverInfo.getPassword(), serverInfo.isAuthPreemptive(), true);
				//base.options();
				

				// (2/4) get user URL
				
				// (3/4) get collections
				
				// (4/4) get address book

				
			} catch (Exception e) {
				Log.e(TAG, "Error while querying server info", e);
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_exfio, e.getLocalizedMessage()));
			}
			
			return serverInfo;
		}
	}
}
