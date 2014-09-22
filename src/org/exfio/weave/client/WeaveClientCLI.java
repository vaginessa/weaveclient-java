package org.exfio.weave.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.OSUtils;

public class WeaveClientCLI {

	//Config file
	public static final String CONFIG_PATH         = ".exfioweave";
	public static final String CONFIG_GLOBAL_FILE  = "exfioweave";
	public static final String CONFIG_CLIENT_FILE  = "account";

	public static final String KEY_ACCOUNT_CONFIG_SERVER   = "server";
	public static final String KEY_ACCOUNT_CONFIG_USERNAME = "username";
	public static final String KEY_ACCOUNT_CONFIG_SYNCKEY  = "synckey";

	//**********************************
	// CLI interface and helper methods
	//**********************************

	public static File buildGlobalConfigPath() {
		//Build path to global config file
		File configPath = new File(System.getProperty("user.home"), WeaveClientCLI.CONFIG_PATH);
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
		File configPath = new File(System.getProperty("user.home"), WeaveClientCLI.CONFIG_PATH);
		return new File(configPath, String.format("%s.%s.properties", WeaveClientCLI.CONFIG_CLIENT_FILE, configKey));
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
		formatter.printHelp( "weaveclient", options );
	}
	
	public static void main( String[] args ) {
		
		String baseURL    = null;
		String username   = null;
		String password   = null;
		String synckey    = null;
		String email      = null;
		String collection = null;
		String id         = null;
		String payload    = null;
		boolean delete    = false;
		boolean info      = false;
		boolean encrypt   = true;
		String loglevel   = null;
		
		WeaveClientFactory.StorageVersion storageVersion = null;
		
		// Parse commandline arguments
		Options options = new Options();
		
		options.addOption("h", "help", false, "print this message");
		options.addOption("s", "server", true, "server URL");
		options.addOption("u", "username", true, "username");
		options.addOption("p", "password", true, "password");
		options.addOption("r", "register", true, "register");
		options.addOption("e", "email", true, "email");
		options.addOption("v", "storage-version", true, "storage version (auto|5). Defaults to auto");	
		options.addOption("k", "sync-key", true, "sync key (required for storage v5)");
		options.addOption("f", "config-file", true, "load config from file");
		options.addOption("a", "account", true, "load config for account");
		options.addOption("c", "collection", true, "collection");
		options.addOption("i", "id", true, "object ID");
		options.addOption("t", "plaintext", false, "do not encrypt/decrypt item");
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

		// DEBUG only
		System.out.println(String.format("os: %s, distro: %s, hostname: %s, prettyname: %s", OSUtils.getOS(), OSUtils.getDistro(), OSUtils.getHostName(), OSUtils.getPrettyName()));

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

		//Is this a registration
		if ( cmd.hasOption('r') ) {
			
			String accountName = cmd.getOptionValue('r');

			//Set host and credential details
			baseURL  = cmd.getOptionValue('s');
			username = cmd.getOptionValue('u');
			password = cmd.getOptionValue('p');
			email    = cmd.getOptionValue('e');

			if (
				(baseURL == null || baseURL.isEmpty())
				||
				(username == null || username.isEmpty())
				||
				(password == null || password.isEmpty())
				||
				(email == null || email.isEmpty())
			) {
				System.err.println("server, username, password and email are required parameters for registration");
				System.exit(1);
			}

			//Validate URI syntax
			try {
				URI.create(baseURL);
			} catch (IllegalArgumentException e) {
				System.err.printf("'%s' is not a valid URI, i.e. should be http(s)://example.com\n", baseURL);
				System.exit(1);
			}			

			//Set storage version
			if ( cmd.hasOption('v') && !cmd.getOptionValue('v').equalsIgnoreCase("auto") ) {
				storageVersion = WeaveClientFactory.stringToStorageVersion(cmd.getOptionValue('v'));
			} else {
				//Default to v5
				storageVersion = WeaveClientFactory.StorageVersion.v5;
			}

			Log.getInstance().info(String.format("Registering new account, user: '%s', pass: '%s'", username, password));
			
			WeaveClient wc = null;
			AccountParams wcParams = null;
			try {			
				RegistrationParams  regParams = new RegistrationParams();
				regParams.baseURL  = baseURL;
				regParams.user     = username;
				regParams.password = password;
				regParams.email    = email;
				
				wc = WeaveClientFactory.getInstance(storageVersion);
				wcParams = wc.register(regParams);

			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
			System.out.println(String.format("Successfully registered account for user: '%s'", username));
			
			if ( wc.getStorageVersion() == WeaveClientFactory.StorageVersion.v5 ) {
				
				//Generate config key
				String configKey = null;
				try {
					configKey = URLEncoder.encode(accountName, "UTF-8");
				} catch (UnsupportedEncodingException e) {
					System.err.println(e.getMessage());
					System.exit(1);
				}

				//Save account details
				WeaveClientV5Params clientParams = (WeaveClientV5Params)wcParams;
				
				System.out.println(String.format("Storage v5 sync key: '%s'", clientParams.syncKey));
				
				//Create client config
				File clientConfig = buildAccountConfigPathForKey(configKey);			
				Properties clientProp = new Properties();
				
				clientProp.setProperty(KEY_ACCOUNT_CONFIG_SERVER, clientParams.baseURL);
				clientProp.setProperty(KEY_ACCOUNT_CONFIG_USERNAME, clientParams.user);
				clientProp.setProperty(KEY_ACCOUNT_CONFIG_SYNCKEY, clientParams.syncKey);
				
				try {
					clientConfig.getParentFile().mkdirs();
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
				
			} else {
				Log.getInstance().warn(String.format("Don't know how to store params for storage version %s", WeaveClientFactory.storageVersionToString(wc.getStorageVersion())));
			}
			
			System.exit(0);
		}
		
		//Load client config
		Properties clientProp = new Properties();
		File clientConfig = null;
		
		if ( cmd.hasOption('f') || cmd.hasOption('a') ) {
			try {
				if ( cmd.hasOption('f') ) {
					clientConfig = new File(cmd.getOptionValue('f'));
				} else if ( cmd.hasOption('a') ) {
					clientConfig = WeaveClientCLI.buildAccountConfigPath(cmd.getOptionValue('a'));
				}
				
				clientProp.load(new FileInputStream(clientConfig));
				
			} catch (IOException e) {
				System.err.println(String.format("Couldn't load client config - %s", e.getMessage()));
				System.exit(1);
			} catch (AccountNotFoundException e) {
				System.err.println(String.format("Couldn't load client config - %s", e.getMessage()));
				System.exit(1);
			}

			//Set host and credential details from config file
			baseURL  = clientProp.getProperty(KEY_ACCOUNT_CONFIG_SERVER);
			username = clientProp.getProperty(KEY_ACCOUNT_CONFIG_USERNAME);
			
		} else {
			
			//Set host and credential details from command line
			baseURL  = cmd.getOptionValue('s', null);
			username = cmd.getOptionValue('u', null);
		}

		password = cmd.getOptionValue('p', null);

		if (
			(baseURL == null || baseURL.isEmpty())
			||
			(username == null || username.isEmpty())
			||
			(password == null || password.isEmpty())
		) {
			System.err.println("server, username and password are required parameters");
			System.exit(1);
		}

		//Validate URI syntax
		try {
			URI.create(baseURL);
		} catch (IllegalArgumentException e) {
			System.err.printf("'%s' is not a valid URI, i.e. should be http(s)://example.com\n", baseURL);
			System.exit(1);
		}			

		//Set storage version
		if ( cmd.hasOption('v') && !cmd.getOptionValue('v').equalsIgnoreCase("auto") ) {
			storageVersion = WeaveClientFactory.stringToStorageVersion(cmd.getOptionValue('v'));
		} else {
			//Auto-discover storage version if not explicitly set
			try {
				AccountParams adParams = new AccountParams();
				adParams.baseURL = baseURL;
				adParams.user    = username;
				adParams.password = password;				
				storageVersion = WeaveClientFactory.autoDiscoverStorageVersion(adParams);
			} catch (WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}		
		}
		
		if ( storageVersion == WeaveClientFactory.StorageVersion.v5 ) {
			//Only v5 is currently supported
			
			if ( cmd.hasOption('f') || cmd.hasOption('a') ) {
				//Get synckey from config file
				synckey = clientProp.getProperty(KEY_ACCOUNT_CONFIG_SYNCKEY, null);
			} else {
				//Get synckey from command line
				synckey = cmd.getOptionValue('k', null);				
			}

			if ( synckey == null || synckey.isEmpty() ) {
				System.err.println("sync-key is a required parameter for storage version 5");
				System.exit(1);
			}
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

		WeaveClient weaveClient = null;
		
		if ( storageVersion == WeaveClientFactory.StorageVersion.v5 ){
			//Only v5 is currently supported
			WeaveClientV5Params weaveParams = new WeaveClientV5Params();
			weaveParams.baseURL = baseURL;
			weaveParams.user = username;
			weaveParams.password = password;
			weaveParams.syncKey = synckey;
			
			try {
				weaveClient = WeaveClientFactory.getInstance(storageVersion);
				weaveClient.init(weaveParams);	
			} catch (WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}			
		} else {
			System.err.println("Storage version not recognised");
			System.exit(1);
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
