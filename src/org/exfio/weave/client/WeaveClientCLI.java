/*******************************************************************************
 * Copyright (c) 2014 Gerry Healy <nickel_chrome@mac.com>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Gerry Healy <nickel_chrome@mac.com> - Initial implementation
 ******************************************************************************/
package org.exfio.weave.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.exfio.weave.AccountNotFoundException;
import org.exfio.weave.WeaveException;
import org.exfio.weave.account.WeaveAccount;
import org.exfio.weave.account.WeaveAccountCLI;
import org.exfio.weave.account.WeaveAccountParams;
import org.exfio.weave.account.fxa.FxAccount;
import org.exfio.weave.account.fxa.FxAccountParams;
import org.exfio.weave.account.legacy.WeaveSyncV5Account;
import org.exfio.weave.account.legacy.WeaveSyncV5AccountParams;
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;
import org.exfio.weave.storage.NotFoundException;
import org.exfio.weave.storage.WeaveBasicObject;
import org.exfio.weave.storage.WeaveCollectionInfo;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.OSUtils;

public class WeaveClientCLI {

	//**********************************
	// CLI interface and helper methods
	//**********************************

	public static void printUsage(Options options) {
		System.out.println();
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp( "weaveclient", options );
	}
	
	public static void main( String[] args ) {
		
		String accountServer = null;
		String tokenServer   = null;
		String username      = null;
		String password      = null;
		String synckey       = null;
		String collection    = null;
		String id            = null;
		String payload       = null;
		boolean delete       = false;
		boolean info         = false;
		boolean encrypt      = true;
		String loglevel      = null;
		
		WeaveClientFactory.ApiVersion apiVersion = null;
		
		// Parse commandline arguments
		Options options = new Options();
		
		options.addOption("h", "help", false, "print this message");

		//Initialiseb account from local storage
		options.addOption("a", "account", true, "load config for account");
		options.addOption("f", "config-file", true, "load config from file");
		options.addOption("p", "password", true, "password");

		//Initialise account from commandline parameters
		options.addOption("v", "api-version", true, "api version (auto|1.1|1.5). Defaults to 1.1");
		options.addOption("s", "account-server", true, "account server URL");
		options.addOption("t", "token-server", true, "token server URL");
		options.addOption("u", "username", true, "username");
		options.addOption("k", "sync-key", true, "sync key");
		
		//Weave sync storage request parameters
		options.addOption("c", "collection", true, "collection");
		options.addOption("i", "id", true, "object ID");
		options.addOption("", "plaintext", false, "do not encrypt/decrypt item");
		options.addOption("m", "modify", true, "update item with given value in JSONUtils format. Requires -c and -i");
		options.addOption("d", "delete", false, "delete item. Requires -c and -i");
		options.addOption("n", "info", false, "get collection info. Requires -c");
		
		options.addOption("l", "log-level", true, "set log level (trace|debug|info|warn|error)");
		
		CommandLineParser parser = new GnuParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse( options, args );
		} catch( ParseException exp ) {
			System.err.println( "Parsing failed: " + exp.getMessage() );
			System.exit(1);
		}
		
		if ( cmd.hasOption('h') ) {
			// help
			printUsage(options);
			System.exit(0);
		}

		//Need to set log level BEFORE instansiating Logger
		loglevel = "warn";
		if ( cmd.hasOption('l') ) {
			loglevel = cmd.getOptionValue('l').toLowerCase();
			if ( !loglevel.matches("trace|debug|info|warn|error") ) {
				System.err.println("log level must be one of (trace|debug|info|warn|error)");
				System.exit(1);		
			}
		}
		Log.init(loglevel);
		
		//DEBUG only
		//Log.getInstance().warn("Log warn message");
		//Log.getInstance().info("Log info message");
		//Log.getInstance().debug("Log debug message");

		// OS details
		Log.getInstance().debug(String.format("os: %s, distro: %s, hostname: %s, prettyname: %s", OSUtils.getOS(), OSUtils.getDistro(), OSUtils.getHostName(), OSUtils.getPrettyName()));

		// Intialise account
		WeaveAccount account = null;
		
		if ( cmd.hasOption('f') || cmd.hasOption('a') ) {

			//Get account params from local storage
			
			if ( password == null || password.isEmpty() ) {
				System.err.println("password is a required parameters");
				System.exit(1);
			}

			Properties clientProp = new Properties();
			try {
				File clientConfig               = null;
				
				if ( cmd.hasOption('f') ) {
					clientConfig = new File(cmd.getOptionValue('f'));
				} else if ( cmd.hasOption('a') ) {
					clientConfig = WeaveAccountCLI.buildAccountConfigPath(cmd.getOptionValue('a'));
				}
				
				clientProp.load(new FileInputStream(clientConfig));
				
			} catch (IOException e) {
				System.err.println(String.format("Couldn't load client config - %s", e.getMessage()));
				System.exit(1);
			} catch (AccountNotFoundException e) {
				System.err.println(String.format("Couldn't load client config - %s", e.getMessage()));
				System.exit(1);
			}

			String apiVersionString = clientProp.getProperty(WeaveAccount.KEY_ACCOUNT_CONFIG_APIVERSION, null);
			if ( apiVersionString == null || apiVersionString.isEmpty() ) {
				System.err.println("apiversion is a required config parameter");
				System.exit(1);				
			}

			apiVersion = WeaveClientFactory.stringToApiVersion(apiVersionString);
			
			if ( apiVersion == WeaveClientFactory.ApiVersion.v1_1 ) {
				try {
					account = new WeaveSyncV5Account();
					account.init(clientProp, password);
				} catch (WeaveException e) {
					System.err.println(String.format("Couldn't initialise Weave Sync account - %s", e.getMessage()));
					System.exit(1);
				}
			} else if ( apiVersion == WeaveClientFactory.ApiVersion.v1_5 ) {
					try {
						account = new FxAccount();
						account.init(clientProp, password);
					} catch (WeaveException e) {
						System.err.println(String.format("Couldn't initialise Weave Sync account - %s", e.getMessage()));
						System.exit(1);
					}
			} else {
				System.err.println("Storage version not recognised");
				System.exit(1);
			}					
			
		} else {
			
			//Get account params from command line

			//Get storage version
			if ( cmd.hasOption('v') && !cmd.getOptionValue('v').equalsIgnoreCase("auto") ) {
				apiVersion = WeaveClientFactory.stringToApiVersion(cmd.getOptionValue('v'));
			} else {
				//TODO - Auto-discover API version if not explicitly set
				System.err.println("Auto discover not implemented");
				System.exit(1);
			}
			
			if (apiVersion == ApiVersion.v1_1) {
				
				//Get host and credential details
				accountServer = cmd.getOptionValue('s');
				username      = cmd.getOptionValue('u');
				password      = cmd.getOptionValue('p');
				synckey       = cmd.getOptionValue('k');
				
				if (
					(accountServer == null || accountServer.isEmpty())
					||
					(username == null || username.isEmpty())
					||
					(password == null || password.isEmpty())
					||
					(synckey == null || synckey.isEmpty())
				) {
					System.err.println("server, username, password and synckey are required parameters for account registration");
					System.exit(1);
				}
	
				//Validate URI syntax
				try {
					URI.create(accountServer);
				} catch (IllegalArgumentException e) {
					System.err.printf("'%s' is not a valid URI, i.e. should be http(s)://example.com\n", accountServer);
					System.exit(1);
				}			
	
				WeaveSyncV5AccountParams fslParams = new WeaveSyncV5AccountParams();
				fslParams.accountServer  = accountServer;
				fslParams.user           = username;
				fslParams.password       = password;
				fslParams.syncKey        = synckey;
				
				try {
					account = new WeaveSyncV5Account();
					account.init(fslParams);
				} catch (WeaveException e) {
					System.err.println(String.format("Couldn't initialise account - %s", e.getMessage()));
					System.exit(1);
				}

			} else if (apiVersion == ApiVersion.v1_5) {
					
				//Get host and credential details
				accountServer = cmd.getOptionValue('s');
				tokenServer   = cmd.getOptionValue('t');
				username      = cmd.getOptionValue('u');
				password      = cmd.getOptionValue('p');
				
				if (
					(accountServer == null || accountServer.isEmpty())
					||
					(tokenServer == null || tokenServer.isEmpty())
					||
					(username == null || username.isEmpty())
					||
					(password == null || password.isEmpty())
				) {
					System.err.println("account-server, token-server, username and password are required parameters for account registration");
					System.exit(1);
				}
	
				//Validate URI syntax
				try {
					URI.create(accountServer);
				} catch (IllegalArgumentException e) {
					System.err.printf("'%s' is not a valid URI, i.e. should be http(s)://example.com\n", accountServer);
					System.exit(1);
				}			
	
				FxAccountParams fxaParams = new FxAccountParams();
				fxaParams.accountServer  = accountServer;
				fxaParams.tokenServer    = tokenServer;
				fxaParams.user           = username;
				fxaParams.password       = password;
				
				try {
					account = new FxAccount();
					account.init(fxaParams);
				} catch (WeaveException e) {
					System.err.println(String.format("Couldn't initialise account - %s", e.getMessage()));
					System.exit(1);
				}

			} else {
				Log.getInstance().warn(String.format("API version %s not supported", apiVersion));
			}				
			
		}
		
		WeaveAccountParams clientParams = account.getAccountParams();
		
		Log.getInstance().debug(String.format("Account params:\n%s", clientParams));
		
		//Initialise weave client from account params
		WeaveClient weaveClient = null;
		
		try {
			weaveClient = WeaveClientFactory.getInstance(clientParams);
		} catch (WeaveException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
		
		//Set collection
		collection = cmd.getOptionValue('c', null);
		if ( collection == null || collection.isEmpty() ) {
			System.err.println("collection is a required parameter");
			System.exit(1);
		}

		//Optionally get ID
		if ( cmd.hasOption('i') ) {
			id = cmd.getOptionValue('i');
		}

		if ( cmd.hasOption('m') ) {
			if ( id == null ) {
				System.err.println("id is required when using the modify option");
				System.exit(1);
			}
			payload = cmd.getOptionValue('m');
		}

		if ( cmd.hasOption('d') ) {
			if ( id == null ) {
				System.err.println("id is required when using the delete option");
				System.exit(1);
			} else if ( payload != null ) {
				System.err.println("the modify and delete options cannot be used together");
				System.exit(1);
			}
			delete = true;
		}

		if ( cmd.hasOption('n') ) {
			if ( !( id == null && payload == null ) ) {
				//quietly do nothing
			} else {
				info = true;
			}
		}

		if ( cmd.hasOption('t') ) {
			encrypt = false;
		}

		if ( payload != null ) {
			
			WeaveBasicObject wbo = new WeaveBasicObject(id, null, null, null, payload);

			Double modified = null;
			try {
				modified = weaveClient.put(collection, id, wbo, encrypt);
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			System.out.println(String.format("modified: %f", modified));

		} else if ( delete ) {
			
			try {
				weaveClient.delete(collection, id);
			} catch (NotFoundException e) {
				System.err.println(String.format("Collection '%s' item '%s' not found - " + e.getMessage(), collection, id));
				System.exit(1);
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}

			//TODO - Handle collections

		} else if ( info ) {
			
			WeaveCollectionInfo colinfo = null;
			try {
				colinfo = weaveClient.getCollectionInfo(collection);
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			} catch(NotFoundException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}

			System.out.println(colinfo.toString());

		} else {
			
			try {
				if ( id != null ) {
					WeaveBasicObject wbo = weaveClient.get(collection, id, encrypt);
					System.out.print(wbo.getPayload());
				} else {
					WeaveBasicObject[] colWbo = weaveClient.getCollection(collection, null, null, null, null, null, null, null, null, null, encrypt);
					for (int i = 0; i < colWbo.length; i++) {
						System.out.println(colWbo[i].getPayload().trim());
					}	
				}
			} catch(NotFoundException e) {
				System.err.println(String.format("Weave object not found - %s", e.getMessage()));
				System.exit(1);				
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
		}
	}
}
