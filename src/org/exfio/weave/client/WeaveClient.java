package org.exfio.weave.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.json.simple.JSONObject;
import org.exfio.weave.WeaveException;
import org.exfio.weave.client.WeaveStorageContext;
import org.exfio.weave.client.WeaveStorageV5;
import org.exfio.weave.util.Log;

public class WeaveClient {

	//Config file
	public static final String CONFIG_PATH         = ".exfioweave";
	public static final String CONFIG_GLOBAL_FILE  = "exfioweave";
	public static final String CONFIG_CLIENT_FILE  = "account";

	private WeaveStorageContext ws;
	
	public enum StorageVersion {
		v5;
	}

	public enum ApiVersion {
		v1_1;
	}

	protected WeaveClient(WeaveStorageContext ws) {
		this.ws = ws;
	}

	public static String storageVersionToString(StorageVersion version) {
		String versionString = null;
		switch (version) {
		case v5:
			versionString = "5";
			break;
		default:
		}
		return versionString;
	}

	public static StorageVersion stringToStorageVersion(String version) {
		StorageVersion storageVersion = null;
		if ( version.equals("5") ) {
			storageVersion = StorageVersion.v5;
		}
		return storageVersion;
	}

	public static final StorageVersion autoDiscoverStorageVersion(WeaveBasicParams adParams) throws WeaveException {
		StorageVersion storageVersion = null;
		
		WeaveApiClient apiClient = WeaveApiClient.getInstance(ApiVersion.v1_1);
		apiClient.init(adParams.baseURL, adParams.user, adParams.password);
		
		WeaveBasicObject wbo = null;
		try {
			wbo = apiClient.get(WeaveStorageV5.KEY_META_PATH);
		} catch (NotFoundException e) {
			throw new WeaveException(WeaveStorageV5.KEY_META_PATH + " not found " + e.getMessage());
		}
		JSONObject jsonPayload = null;
		
		try {
			jsonPayload = wbo.getPayloadAsJSONObject();
		} catch (org.json.simple.parser.ParseException e) {
			throw new WeaveException(e);
		}
		
		if ( !jsonPayload.containsKey("storageVersion") ) {
			throw new WeaveException(String.format("Storage version not found in %s record", WeaveStorageV5.KEY_META_PATH));
		}

		Long version = null;
		if (jsonPayload.get("storageVersion").getClass().equals(Long.class)) {
			version = (Long)jsonPayload.get("storageVersion");
		} else {
			version = Long.parseLong((String)jsonPayload.get("storageVersion"));
		}
		
		if ( version == 5 ) {
			storageVersion = StorageVersion.v5;
		} else {
			throw new WeaveException(String.format("Storage version '%s' not supported", version));
		}
		
		return storageVersion;
	}

	public static final WeaveClient getInstance(WeaveClientParams params) throws WeaveException {
		//return WeaveClient for given parameters
		WeaveClient weaveClient = null;
		
		if ( params.getStorageVersion() == null ) {
			//auto-discovery
			StorageVersion storageVersion = autoDiscoverStorageVersion((WeaveBasicParams)params);
			weaveClient = getInstance(storageVersion);
		} else {
			weaveClient = getInstance(params.getStorageVersion());
			weaveClient.init(params);
		}
		
		return weaveClient;
	}

	public static final WeaveClient getInstance(StorageVersion storageVersion) throws WeaveException {
		//return WeaveClient for given storage context
		
		WeaveStorageContext context = null;
		
		switch(storageVersion) {
		case v5:
			context = new WeaveStorageV5();
			break;
		default:
			throw new WeaveException(String.format("Weave storage version '%s' not recognised", storageVersion));
		}
		
		return new WeaveClient(context);
	}

	//**********************************
	// Weave Client Public Methods
	//**********************************
	
	public void init(WeaveClientParams params) throws WeaveException {
		ws.init(params);
	}

	public void registerAccount(WeaveClientParams params) throws WeaveException {
		ws.register(params);
	}

	public StorageVersion getStorageVersion() {
		return ws.getStorageVersion();
	}

	public WeaveClientParams getClientParams() {
		return ws.getClientParams();
	}

	public String generateWeaveID() {
		return ws.generateWeaveID();
	}
	
	public WeaveBasicObject decryptWeaveBasicObject(WeaveBasicObject wbo) throws WeaveException {
		return decryptWeaveBasicObject(wbo, null);
	}
	public WeaveBasicObject decryptWeaveBasicObject(WeaveBasicObject wbo, String keyLabel) throws WeaveException {
		return ws.decryptWeaveBasicObject(wbo, keyLabel);
	}

	public WeaveBasicObject getItem(String collection, String id) throws WeaveException, NotFoundException {
		return ws.get(collection, id);
	}

	public WeaveBasicObject getItem(String collection, String id, boolean decrypt) throws WeaveException, NotFoundException {
		return ws.get(collection, id, decrypt);
	}

	public WeaveCollectionInfo getCollectionInfo(String collection) throws WeaveException {
		return ws.getCollectionInfo(collection, true, true);
	}

	public String[] getCollectionIds(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort) throws WeaveException, NotFoundException {
		return ws.getCollectionIds(collection, ids, older, newer, index_above, index_below, limit, offset, sort);
	}

	public WeaveBasicObject[] getCollection(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort, String format) throws WeaveException, NotFoundException {
		return ws.getCollection(collection, ids, older, newer, index_above, index_below, limit, offset, sort, format);
	}

	public WeaveBasicObject[] getCollection(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort, String format, boolean decrypt) throws WeaveException, NotFoundException {
		return ws.getCollection(collection, ids, older, newer, index_above, index_below, limit, offset, sort, format, decrypt);
	}

	public Double putItem(String collection, String id, WeaveBasicObject wbo) throws WeaveException {
		return ws.put(collection, id, wbo);
	}

	public Double putItem(String collection, String id, WeaveBasicObject wbo, boolean encrypt) throws WeaveException {
		return ws.put(collection, id, wbo, encrypt);
	}

	public Double deleteItem(String collection, String id) throws WeaveException, NotFoundException {
		return ws.delete(collection, id);
	}

	public Double deleteCollection(String collection, String[] ids, Double older, Double newer, Integer limit, Integer offset, String sort) throws WeaveException, NotFoundException {
		return ws.deleteCollection(collection, ids, older, newer, limit, offset, sort);
	}

	public void lock() {
		ws.getApiClient().lock();
	}
	
	public void unlock() {
		ws.getApiClient().unlock();
	}
	
	public void close() throws IOException {
		ws.getApiClient().close();
	}
	
	
	//**********************************
	// CLI interface and helper methods
	//**********************************

	public static File buildGlobalConfigPath() {
		//Build path to global config file
		File configPath = new File(System.getProperty("user.home"), WeaveClient.CONFIG_PATH);
		return new File(configPath, "exfioweave.properties");
	}

	public static File buildAccountConfigPath() throws IOException {
		return buildAccountConfigPath(null);
	}
	
	public static File buildAccountConfigPath(String accountName) throws IOException {
		//Get path to config file for accountName

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

		//Get configKey for accountName
		String configKey = globalProp.getProperty("account." + accountName);
		
		return buildAccountConfigPathForKey(configKey);		
	}

	public static File buildAccountConfigPathForKey(String configKey) {
		//Build path to account config file
		File configPath = new File(System.getProperty("user.home"), WeaveClient.CONFIG_PATH);
		return new File(configPath, String.format("%s.%s.properties", WeaveClient.CONFIG_CLIENT_FILE, configKey));		
	}

	public static Properties loadAccountConfig() throws IOException {
		return loadAccountConfig(null);
	}
	
	public static Properties loadAccountConfig(String accountName) throws IOException {
		File configFile = buildAccountConfigPath(accountName);
		Properties prop = new Properties();
		prop.load(new FileInputStream(configFile));
		return prop;
	}

	public static void writeAccountConfig(Properties prop) throws IOException {
		writeAccountConfig(prop, null);
	}

	public static void writeAccountConfig(Properties prop, String accountName) throws IOException {
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
		
		StorageVersion storageVersion = null;
		
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
		options.addOption("m", "modify", true, "update item with given value in JSON format. Requires -c and -i");
		options.addOption("d", "delete", false, "delete item. Requires -c and -i");
		options.addOption("n", "info", false, "get collection info. Requires -c");
		options.addOption("l", "log-level", true, "set log level (trace|debug|info|warn|error)");
		
		CommandLineParser parser = new GnuParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse( options, args );
		} catch( ParseException exp ) {
			System.err.println( "Parsing failed: " + exp.getMessage() );
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
				storageVersion = stringToStorageVersion(cmd.getOptionValue('v'));
			} else {
				//Default to v5
				storageVersion = StorageVersion.v5;
			}

			Log.getInstance().info(String.format("Registering new account, user: '%s', pass: '%s'", username, password));
			
			WeaveClient wc = null;
			try {			
				WeaveRegistrationParams  regParams = new WeaveRegistrationParams();
				regParams.baseURL  = baseURL;
				regParams.user     = username;
				regParams.password = password;
				regParams.email    = email;
				
				wc = WeaveClient.getInstance(storageVersion);
				wc.registerAccount(regParams);

			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
			System.out.println(String.format("Successfully registered account for user: '%s'", username));
			
			if ( wc.getStorageVersion() == StorageVersion.v5 ) {
				
				//Generate config key
				String configKey = wc.generateWeaveID();

				//Save account details
				WeaveStorageV5Params clientParams = (WeaveStorageV5Params)wc.getClientParams();

				System.out.println(String.format("Storage v5 sync key: '%s'", clientParams.syncKey));

				//Create client config
				File clientConfig = buildAccountConfigPathForKey(configKey);			
				Properties clientProp = new Properties();
				
				clientProp.setProperty("server", clientParams.baseURL);
				clientProp.setProperty("username", clientParams.user);
				clientProp.setProperty("synckey", clientParams.syncKey);
				
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
				Log.getInstance().warn(String.format("Don't know how to store params for storage version %s", WeaveClient.storageVersionToString(wc.getStorageVersion())));
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
					clientConfig = WeaveClient.buildAccountConfigPath(cmd.getOptionValue('a'));
				}
				
				clientProp.load(new FileInputStream(clientConfig));
				
			} catch (IOException e) {
				System.err.println(String.format("Couldn't load client config - %s", e.getMessage()));
				System.exit(1);
			}

			//Set host and credential details from config file
			baseURL  = clientProp.getProperty("server");
			username = clientProp.getProperty("username");
			
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
			storageVersion = stringToStorageVersion(cmd.getOptionValue('v'));
		} else {
			//Auto-discover storage version if not explicitly set
			try {
				WeaveBasicParams  adParams = new WeaveBasicParams();
				adParams.baseURL = baseURL;
				adParams.user    = username;
				adParams.password = password;				
				storageVersion = WeaveClient.autoDiscoverStorageVersion(adParams);
			} catch (WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}		
		}
		
		if ( storageVersion == WeaveClient.StorageVersion.v5 ) {
			//Only v5 is currently supported
			
			if ( cmd.hasOption('f') || cmd.hasOption('a') ) {
				//Get synckey from config file
				synckey = clientProp.getProperty("synckey", null);
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
		
		if ( storageVersion == WeaveClient.StorageVersion.v5 ){
			//Only v5 is currently supported
			WeaveStorageV5Params weaveParams = new WeaveStorageV5Params();
			weaveParams.baseURL = baseURL;
			weaveParams.user = username;
			weaveParams.password = password;
			weaveParams.syncKey = synckey;
			
			try {
				weaveClient = WeaveClient.getInstance(storageVersion);
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
				modified = weaveClient.putItem(collection, id, wbo, encrypt);
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			System.out.println(String.format("modified: %f", modified));

		} else if ( delete ) {
			
			try {
				weaveClient.deleteItem(collection, id);
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
					WeaveBasicObject wbo = weaveClient.getItem(collection, id, encrypt);
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
