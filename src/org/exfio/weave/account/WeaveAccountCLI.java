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
package org.exfio.weave.account;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.exfio.weave.AccountNotFoundException;
import org.exfio.weave.WeaveException;
import org.exfio.weave.account.exfiopeer.ExfioPeerV1;
import org.exfio.weave.account.exfiopeer.ClientAuthRequestMessage;
import org.exfio.weave.account.exfiopeer.comm.Message;
import org.exfio.weave.account.fxa.FxAccountParams;
import org.exfio.weave.account.fxa.FxAccount;
import org.exfio.weave.account.legacy.LegacyV5Account;
import org.exfio.weave.account.legacy.LegacyV5AccountParams;
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.client.WeaveClientFactory;
import org.exfio.weave.client.WeaveClientRegistrationParams;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;
import org.exfio.weave.util.Log;

public class WeaveAccountCLI {

	public static final String binName = "weaveaccount";
	
	//Config file
	public static final String CONFIG_PATH         = ".exfioweave";
	public static final String CONFIG_GLOBAL_FILE  = "exfioweave";
	public static final String CONFIG_CLIENT_FILE  = "account";
	public static final String CONFIG_KEY_APIVERSION = "apiversion";

	//**********************************
	// CLI interface and helper methods
	//**********************************

	public static File buildAccountDatabasePath() throws IOException, AccountNotFoundException {
		return buildAccountDatabasePath(null);
	}

	public static File buildAccountDatabasePath(String accountName) throws IOException, AccountNotFoundException {
		//Get path to database file for accountName
		String configKey = getAccountConfigKey(accountName);
		return buildAccountDatabasePathForKey(configKey);
	}

	public static File buildAccountDatabasePathForKey(String configKey) {
		//Build path to account database file
		File configPath   = buildAccountConfigPathForKey(configKey);
		return new File(configPath.getParentFile(), String.format("%s.%s.db", CONFIG_CLIENT_FILE, configKey));
	}

	public static File buildGlobalConfigPath() {
		//Build path to global config file
		File configPath = new File(System.getProperty("user.home"), CONFIG_PATH);
		return new File(configPath, "exfioweave.properties");
	}

	public static File buildAccountConfigPath() throws IOException, AccountNotFoundException {
		return buildAccountConfigPath(null);
	}
	
	public static File buildAccountConfigPath(String accountName) throws IOException, AccountNotFoundException {
		//Get path to config file for accountName
		String configKey = getAccountConfigKey(accountName);
		return buildAccountConfigPathForKey(configKey);
	}

	public static boolean accountExists(String accountName) {
		String accountConfigKey = null;
		try {
			accountConfigKey = getAccountConfigKey(accountName);
		} catch (AccountNotFoundException e) {
			//Indicates account does not exist
		} catch (IOException e) {
			System.err.println(String.format("Error checking for existing account registration - %s", e.getMessage()));
			System.exit(1);
		}
		
		return ( accountConfigKey != null );	
	}
	
	public static String getAccountConfigKey() throws IOException, AccountNotFoundException {
		return getAccountConfigKey(null);
	}
	
	public static String getAccountConfigKey(String accountName) throws IOException, AccountNotFoundException {
		//Get configKey for accountName

		if ( accountName == null ) {
			accountName = "default";
		}
		
		//Load global config file
		File globalFile = buildGlobalConfigPath();
		Properties globalProp = new Properties();
		
		try {
			globalProp.load(new FileInputStream(globalFile));
		} catch (IOException e) {
			throw new IOException(String.format("Couldn't load global config file '%s'", globalFile.getAbsolutePath()));
		}

		String propName = "account." + accountName;
		
		if ( !globalProp.containsKey(propName) ) {
			throw new AccountNotFoundException(String.format("Couldn't find account settings for '%s'", accountName));
		}
		
		return globalProp.getProperty(propName);
	}

	public static File buildAccountConfigPathForKey(String configKey) {
		//Build path to account config file
		File configPath = new File(System.getProperty("user.home"), CONFIG_PATH);
		return new File(configPath, String.format("%s.%s.properties", CONFIG_CLIENT_FILE, configKey));
	}

	public static Properties loadAccountConfig() throws IOException, AccountNotFoundException {
		return loadAccountConfig(null);
	}
	
	public static Properties loadAccountConfig(String accountName) throws IOException, AccountNotFoundException {
		File configFile = buildAccountConfigPath(accountName);
		Properties prop = new Properties();
		prop.load(new FileInputStream(configFile));
		return prop;
	}

	public static void writeAccountConfig(Properties prop) throws IOException, AccountNotFoundException {
		writeAccountConfig(prop, null);
	}

	public static void writeAccountConfig(Properties prop, String accountName) throws IOException, AccountNotFoundException {
		//Build path to config file
		File configFile = buildAccountConfigPath(accountName);
		configFile.getParentFile().mkdirs();
		prop.store(new FileOutputStream(configFile), "");
	}
	
	public static void printUsage(Options options) {
		System.out.println();
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp( binName, options );
	}
	
	public static void main( String[] args ) {
		
		String accountServer = null;
		String tokenServer   = null;
		String username      = null;
		String email         = null;
		String password      = null;
		String synckey       = null;
		String loglevel      = null;
		
		WeaveClientFactory.ApiVersion apiVersion = null;
		
		//for (int i = 0; i < args.length; i++) {
		//	System.out.println(String.format("args[%d]: %s", i, args[i]));
		//}
		
		
		//File binFile = new File(WeaveAccountCLI.class.getProtectionDomain()
		//		  .getCodeSource()
		//		  .getLocation()
		//		  .getPath());
		
		// Parse commandline arguments
		Options options = new Options();
		
		options.addOption("h", "help", false, "print this message");
		
		//Initialise account from local storage
		options.addOption("a", "account", true, "load config and database for account");
		options.addOption("f", "config-file", true, "load config from file");
		options.addOption("p", "password", true, "password");

		//Create account or register device and save to local storage
		options.addOption("n", "create-account", true, "create account");
		options.addOption("r", "register-account", true, "register existing account for this device");
		options.addOption("v", "api-version", true, "api version (auto|1.1|1.5). Defaults to 1.1");
		options.addOption("s", "account-server", true, "account server URL");
		options.addOption("t", "token-server", true, "token server URL");
		options.addOption("u", "username", true, "username");
		options.addOption("e", "email", true, "email");
		options.addOption("k", "synckey", true, "synckey");

		//TODO - change password etc
		
		//Get status of account
		options.addOption(null, "status", false, "account status");

		//Exfio Peer accounts only
		options.addOption("i", "auth-init", true, "reset client authorisation. WARNING all clients will need to re-authenticate");	
		options.addOption("x", "auth-reject", true, "reject client authorisation request");	
		options.addOption("o", "auth-approve", true, "approve client authorisation request");	
		options.addOption("c", "auth-code", true, "verification code for client authorisation");	
		options.addOption("m", "messages", false, "check for new messages");
		
		options.addOption("l", "log-level", true, "set log level (trace|debug|info|warn|error)");
		
		CommandLineParser parser = new GnuParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse( options, args );
		} catch( ParseException exp ) {
			System.err.println( "Parsing failed: " + exp.getMessage() );
			printUsage(options);
			System.exit(1);;
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

		//Create and/or register account
		if ( cmd.hasOption('n') || cmd.hasOption('r') ) {

			String accountName           = null;
			WeaveAccountParams  wcParams = null;
			WeaveAccount account         = null;

			//Set storage version
			if ( cmd.hasOption('v') && !cmd.getOptionValue('v').equalsIgnoreCase("auto") ) {
				apiVersion = WeaveClientFactory.stringToApiVersion(cmd.getOptionValue('v'));
			} else {
				System.err.println("Auto discovery not implemented");
				System.exit(1);
			}

			if ( cmd.hasOption('r') ) {
				
				//Register an existing account
				
				accountName = cmd.getOptionValue('r');

				if ( accountExists(accountName) ) {
					//account already registered under this name
					System.err.println(String.format("Account with name '%s' already registered", accountName));
					System.exit(1);
				}
				
				Log.getInstance().debug(String.format("Registering account '%s'", accountName));

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
		
					LegacyV5AccountParams fslParams = new LegacyV5AccountParams();
					fslParams.accountServer  = accountServer;
					fslParams.user           = username;
					fslParams.password       = password;
					fslParams.syncKey        = synckey;
					
					try {
						account = new LegacyV5Account();
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

			} else if ( cmd.hasOption('n') ) {
				
				//Create new account
				
				accountName = cmd.getOptionValue('n');

				if ( accountExists(accountName) ) {
					//account already registered under this name
					System.err.println(String.format("Account with name '%s' already registered", accountName));
					System.exit(1);
				}

				if ( apiVersion == ApiVersion.v1_1 ) {

					//Set host and credential details
					accountServer = cmd.getOptionValue('s');
					username      = cmd.getOptionValue('u');
					password      = cmd.getOptionValue('p');
					email         = cmd.getOptionValue('e');

					Log.getInstance().debug(String.format("Creating %s account '%s'@%s", WeaveClientFactory.apiVersionToString(apiVersion), username, accountServer));

					if (
						(accountServer == null || accountServer.isEmpty())
						||
						(username == null || username.isEmpty())
						||
						(password == null || password.isEmpty())
						||
						(email == null || email.isEmpty())
					) {
						System.err.println("server, username, password and email are required parameters for account creation");
						System.exit(1);
					}
		
					//Validate URI syntax
					try {
						URI.create(accountServer);
					} catch (IllegalArgumentException e) {
						System.err.printf("'%s' is not a valid URI, i.e. should be http(s)://example.com\n", accountServer);
						System.exit(1);
					}			
			
					Log.getInstance().info(String.format("Creating new account, user: '%s', pass: '%s'", username, password));
							
					WeaveAccountParams  regParams = new LegacyV5AccountParams();					
					regParams.accountServer  = accountServer;
					regParams.user           = username;
					regParams.password       = password;
					regParams.email          = email;
					
					try {
						account = new LegacyV5Account();
						account.createAccount(regParams);
					} catch (WeaveException e) {
						System.err.println(String.format("Couldn't create account - %s", e.getMessage()));
						System.exit(1);
					}
					
					System.out.println(String.format("Successfully created account for user: '%s'", username));
					
				} else {
					Log.getInstance().warn(String.format("API version %s not supported", apiVersion));
				}

			}

			//Register device as sync client
			WeaveClientRegistrationParams regParams = new WeaveClientRegistrationParams();
			regParams.clientName = accountName;
			
			WeaveClient wc = null;
			try {
				wc = WeaveClientFactory.getInstance(account);
				wc.registerClient(regParams);
			} catch (WeaveException e) {
				System.err.println(String.format("Couldn't register client - %s", e.getMessage()));
				System.exit(1);
			}

			wcParams = account.getAccountParams();
			Log.getInstance().debug(String.format("Registered client '%s' for v%s account '%s'@%s", accountName, WeaveClientFactory.apiVersionToString(wcParams.getApiVersion()), wcParams.user, wcParams.accountServer));
			
			//Generate config key
			String configKey = null;
			try {
				configKey = URLEncoder.encode(accountName, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}

			//Create client config
			File clientConfig = buildAccountConfigPathForKey(configKey);
			
			Properties clientProp = account.accountParamsToProperties(false);
			
			try {
				clientProp.store(new FileOutputStream(clientConfig), "");
			} catch (IOException e) {
				throw new AssertionError(String.format("Couldn't write client config file '%s'", clientConfig.getAbsolutePath()));
			}

			//Create global config if required
			File globalConfig = buildGlobalConfigPath();
			Properties globalProp = new Properties();
			
			if ( globalConfig.exists() ) {
				try {
					globalProp.load(new FileInputStream(globalConfig));
				} catch (IOException e) {
					throw new AssertionError(String.format("Couldn't load global config file '%s'", globalConfig.getAbsolutePath()));
				}
			} else {
				globalProp.setProperty("account.default", configKey);
			}

			globalProp.setProperty("account." + accountName, configKey);
			
			try {
				globalProp.store(new FileOutputStream(globalConfig), "");
			} catch (IOException e) {
				throw new AssertionError(String.format("Couldn't write global config file '%s'", globalConfig.getAbsolutePath()));
			}
							
			System.exit(0);
		}
		
		if ( !cmd.hasOption('p') ) {
			System.err.println("password is a required parameter");
			System.exit(1);
		}

		//Load client config
		Properties clientProp = new Properties();
		File clientConfig     = null;
		File clientDatabase = null;
		
		try {
			if ( cmd.hasOption('f') ) {
				clientConfig   = new File(cmd.getOptionValue('f'));
			} else if ( cmd.hasOption('a') ) {
				clientConfig   = buildAccountConfigPath(cmd.getOptionValue('a'));
			} else {
				//Use default config
				clientConfig   = buildAccountConfigPath();
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		} catch (AccountNotFoundException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
		
		try {
			clientProp.load(new FileInputStream(clientConfig));
		} catch (IOException e) {
			System.err.println(String.format("Error opening client config file '%s'", clientConfig.getAbsolutePath()));
			System.exit(1);
		}

		//Get account version
		apiVersion = WeaveClientFactory.stringToApiVersion(clientProp.getProperty("apiVersion"));
		
		//Set host and credential details
		accountServer  = clientProp.getProperty("server", null);
		username = clientProp.getProperty("username", null);
		password = cmd.getOptionValue('p', null);
		
		if (
			apiVersion == null
			||
			(accountServer == null || accountServer.isEmpty())
			||
			(username == null || username.isEmpty())
			||
			(password == null || password.isEmpty())
		) {
			System.err.println("apiVersion, server, username and password are required parameters");
			System.exit(1);
		}

		//Validate URI syntax
		try {
			URI.create(accountServer);
		} catch (IllegalArgumentException e) {
			System.err.printf("'%s' is not a valid URI, i.e. should be http(s)://example.com\n", accountServer);
			System.exit(1);
		}			

		
		if ( cmd.hasOption('t') ) {
			//Account status

			WeaveAccount account = null;

			if ( apiVersion == WeaveClientFactory.ApiVersion.v1_1 ){
				//Only v5 is currently supported
				
				//Get synckey, default to null
				synckey = clientProp.getProperty("synckey", null);
				
				LegacyV5AccountParams fslParams = new LegacyV5AccountParams();
				fslParams.accountServer  = accountServer;
				fslParams.user           = username;
				fslParams.password       = password;
				fslParams.syncKey        = synckey;
				
				try {
					account = new LegacyV5Account();
					account.init(fslParams);
				} catch (WeaveException e) {
					System.err.println(e.getMessage());
					System.exit(1);
				}
				
			} else {
				System.err.println("API version not recognised");
				System.exit(1);
			}		

			System.out.println(account.getStatus());
			System.exit(0);
		}
		
		
		WeaveClient weaveClient = null;
		
		if ( apiVersion == WeaveClientFactory.ApiVersion.v1_1 ){
			//Only v5 is currently supported
			
			//Get synckey, default to null
			synckey = clientProp.getProperty("synckey", null);
			
			LegacyV5AccountParams fslParams = new LegacyV5AccountParams();
			fslParams.accountServer  = accountServer;
			fslParams.user           = username;
			fslParams.password       = password;
			fslParams.syncKey        = synckey;
			
			try {
				weaveClient = WeaveClientFactory.getInstance(apiVersion);
				weaveClient.init(fslParams);
				//Initialise meta data if not yet done
				if ( !weaveClient.isInitialised() ) {
					weaveClient.initServer();
				}				
			} catch (WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}			
		} else {
			System.err.println("API version not recognised");
			System.exit(1);
		}		
		
		if ( cmd.hasOption('i') ) {
			//Initialise client

			//Request client auth
			String clientName = cmd.getOptionValue('i');
			
			try {			
				ExfioPeerV1 auth = new ExfioPeerV1(weaveClient);
				auth.initClientAuth(clientName, clientDatabase.getPath());
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
			System.out.println(String.format("Client auth initialised"));
			System.exit(0);

		} else if ( cmd.hasOption('j') ) {

			//Request client auth
			String clientName = cmd.getOptionValue('j');
			String authCode = null;
			
			Log.getInstance().info(String.format("Requesting client auth for client '%s'", clientName));

			try {
				ExfioPeerV1 auth = new ExfioPeerV1(weaveClient);
				auth.requestClientAuth(clientName, password, clientDatabase.getPath());
				authCode = auth.getAuthCode();
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
			System.out.println(String.format("Client auth request pending with auth code '%s'", authCode));
			System.exit(0);
			
		} else if ( cmd.hasOption('o') ) {
			
			//Approve client auth request
			String clientName = cmd.getOptionValue('o', null);
			String authCode   = cmd.getOptionValue('c', null);

			if ( authCode == null || authCode.isEmpty() ) {
				System.err.println("auth-code is a required argument for auth-approve");
				System.exit(1);
			}

			Log.getInstance().info(String.format("Approving client auth request '%s'", clientName));
			
			boolean caFound = false;
			
			try {			
				ExfioPeerV1 auth = new ExfioPeerV1(weaveClient, clientDatabase.getPath());
				
				Message[] messages = auth.processClientAuthMessages();
								
				for (Message msg: messages) {
					ClientAuthRequestMessage caMsg = (ClientAuthRequestMessage)msg;
					
					if ( caMsg.getClientName().equals(clientName) ) {
						auth.approveClientAuth(caMsg.getMessageSessionId(), authCode, password);
						caFound = true;
						break;
					}
				}
				
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
			if (!caFound) {
				System.out.println(String.format("Client auth request for client '%s' not found", clientName));
				System.exit(1);
			}
			
			System.out.println(String.format("Approved client auth request '%s'", clientName));				
			System.exit(0);
			
		} else if ( cmd.hasOption('x') ) {
			
			//Reject client auth request
			String clientName = cmd.getOptionValue('x');
			
			Log.getInstance().info(String.format("Rejecting client auth request '%s'", clientName));

			boolean caFound = false;
			
			try {			
				ExfioPeerV1 auth = new ExfioPeerV1(weaveClient, clientDatabase.getPath());
				Message[] caMsgs = auth.processClientAuthMessages();
								
				for (int i = 0; i < caMsgs.length; i++) {
					ClientAuthRequestMessage caMsg = (ClientAuthRequestMessage)caMsgs[i];
					
					if ( caMsg.getClientName().equals(clientName) ) {
						auth.rejectClientAuth(caMsg.getMessageSessionId());
						caFound = true;
						break;
					}
				}
				
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
			if (!caFound) {
				System.err.println(String.format("Client auth request for client '%s' not found", clientName));
				System.exit(1);
			}

			System.out.println(String.format("Rejected client auth request '%s'", clientName));
			System.exit(0);

		} else if ( cmd.hasOption('m') ) {

			Log.getInstance().info(String.format("Checking messages"));

			Message[] caMsgs = null;
			
			try {			
				ExfioPeerV1 auth = new ExfioPeerV1(weaveClient, clientDatabase.getPath());
				
				String curStatus = auth.getAuthStatus();
				caMsgs = auth.processClientAuthMessages();
				String newStatus = auth.getAuthStatus();

				if ( curStatus != null && curStatus.equals("pending") ) {
					
					//If client has been authorised update configuration
					if ( newStatus.equals("authorised") ) {
						
						try {
							//FIXME - write account config
							//account.writeConfig(clientConfig);						

							//clientProp.setProperty(KEY_ACCOUNT_CONFIG_SYNCKEY, auth.getSyncKey());
							//clientProp.store(new FileOutputStream(clientConfig), "");
						} catch (Exception e) {
							System.err.println(String.format("Couldn't write client config file '%s'", clientConfig.getAbsolutePath()));
							System.exit(1);;
						}

						System.out.println(String.format("Client auth approved by '%s'", auth.getAuthBy()));
						
					} else if ( newStatus.equals("pending") ) {
						System.out.println(String.format("Client auth request pending with auth code '%s'", auth.getAuthCode()));
					}
				}
				
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
			for (int i = 0; i < caMsgs.length; i++) {
				ClientAuthRequestMessage caMsg = (ClientAuthRequestMessage)caMsgs[i];
				System.out.println(
					String.format(
						"Client auth request received from '%s'.\n"
						+ "To approve request use the command\n"
						+ "%s -o \"%s\" -c AUTHCODE\n"
						+ "To reject request use the command\n"
						+ "%s -x \"%s\"\n", 
						caMsg.getClientName(),
						binName,
						caMsg.getClientName(),						
						binName,
						caMsg.getClientName()						
					)
				);
			}
			
			System.exit(0);
		}

	}
}
