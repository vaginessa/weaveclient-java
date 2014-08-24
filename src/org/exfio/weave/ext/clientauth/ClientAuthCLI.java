package org.exfio.weave.ext.clientauth;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.exfio.weave.WeaveException;
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.client.WeaveClientCLI;
import org.exfio.weave.client.WeaveClientFactory;
import org.exfio.weave.client.AccountParams;
import org.exfio.weave.client.WeaveClientV5Params;
import org.exfio.weave.ext.clientauth.ClientAuth;
import org.exfio.weave.util.Log;

public class ClientAuthCLI {

	public static File buildAccountDatabasePath() throws IOException {
		return buildAccountDatabasePath(null);
	}

	public static File buildAccountDatabasePath(String accountName) throws IOException {
		//Get path to database file for accountName
		String configKey = WeaveClientCLI.getAccountConfigKey(accountName);
		return buildAccountDatabasePathForKey(configKey);
	}

	public static File buildAccountDatabasePathForKey(String configKey) {
		//Build path to account database file
		File configPath   = WeaveClientCLI.buildAccountConfigPathForKey(configKey);
		return new File(configPath.getParentFile(), String.format("%s.%s.db", WeaveClientCLI.CONFIG_CLIENT_FILE, configKey));
	}

	//**********************************
	// CLI interface and helper methods
	//**********************************

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
		String loglevel   = null;
		
		WeaveClientFactory.StorageVersion storageVersion = null;
		
		// Parse commandline arguments
		Options options = new Options();
		
		options.addOption("h", "help", false, "print this message");
		options.addOption("f", "config-file", true, "load config from file");
		options.addOption("d", "database-file", true, "load database from file");		
		options.addOption("a", "account", true, "load config and database for account");
		options.addOption("p", "password", true, "password");
		options.addOption("i", "auth-init", true, "reset client authorisation. WARNING all clients will need to re-authenticate");	
		options.addOption("j", "auth-client", true, "request client authorisation");	
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

		if ( !cmd.hasOption('p') ) {
			System.err.println("password is a required parameter");
			System.exit(1);
		}

		if ( cmd.hasOption('f') != cmd.hasOption('d') ) {
			System.err.println("The parameters config-file and database-file are mutually dependent");
			System.exit(1);		
		}

		//Load client config
		Properties clientProp = new Properties();
		File clientConfig     = null;
		File clientDatabase = null;
		
		try {
			if ( cmd.hasOption('f') && cmd.hasOption('d') ) {
				clientConfig   = new File(cmd.getOptionValue('f'));
				clientDatabase = new File(cmd.getOptionValue('d'));
			} else if ( cmd.hasOption('a') ) {
				clientConfig   = WeaveClientCLI.buildAccountConfigPath(cmd.getOptionValue('a'));
				clientDatabase = buildAccountDatabasePath(cmd.getOptionValue('a'));
			} else {
				//Use default config
				clientConfig   = WeaveClientCLI.buildAccountConfigPath();
				clientDatabase = buildAccountDatabasePath();
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
		
		try {
			clientProp.load(new FileInputStream(clientConfig));
		} catch (IOException e) {
			System.err.println(String.format("Error opening client config file '%s'", clientConfig.getAbsolutePath()));
			System.exit(1);
		}

		//Set host and credential details
		baseURL  = clientProp.getProperty("server", null);
		username = clientProp.getProperty("username", null);
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

		//Auto-discover storage version
		try {
			AccountParams  adParams = new AccountParams();
			adParams.baseURL = baseURL;
			adParams.user    = username;
			adParams.password = password;				
			storageVersion = WeaveClientFactory.autoDiscoverStorageVersion(adParams);
		} catch (WeaveException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}		

		WeaveClient weaveClient = null;
		
		if ( storageVersion == WeaveClientFactory.StorageVersion.v5 ){
			//Only v5 is currently supported
			
			//Get synckey, default to null
			synckey = clientProp.getProperty("synckey", null);
			
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

		if ( cmd.hasOption('j') ) {
			//Request client auth
			String clientName = cmd.getOptionValue('j');

			Log.getInstance().info(String.format("Requesting client auth for client '%s'", clientName));

			try {			
				ClientAuth auth = new ClientAuth(weaveClient);
				auth.authoriseClient(clientName, password, clientDatabase.getPath());
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
			System.out.println(String.format("Sent client auth request for client '%s'", clientName));
			System.exit(0);
		}

		if ( cmd.hasOption('o') ) {
			
			//Approve client auth request
			String messageKey = cmd.getOptionValue('o', null);
			String authCode   = cmd.getOptionValue('c', null);

			if ( authCode == null || authCode.isEmpty() ) {
				System.err.println("auth-code is a required argument for auth-approve");
				System.exit(1);
			}

			Log.getInstance().info(String.format("Approving client auth request '%s'", messageKey));

			try {			
				ClientAuth auth = new ClientAuth(weaveClient, clientDatabase.getPath());
				auth.sendClientAuthResponse(messageKey, true, authCode);
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}

			System.out.println(String.format("Approved client auth request '%s'", messageKey));
			System.exit(0);
			
		} else if ( cmd.hasOption('x') ) {
			
			//Reject client auth request
			String messageKey = cmd.getOptionValue('x');
			
			Log.getInstance().info(String.format("Rejecting client auth request '%s'", messageKey));

			try {			
				ClientAuth auth = new ClientAuth(weaveClient, clientDatabase.getPath());
				auth.sendClientAuthResponse(messageKey, false, null);
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
			System.out.println(String.format("Rejected client auth request '%s'", messageKey));
			System.exit(0);

		} else if ( cmd.hasOption('m') ) {

			Log.getInstance().info(String.format("Checking messages"));

			try {			
				ClientAuth auth = new ClientAuth(weaveClient, clientDatabase.getPath());
				auth.processClientAuthMessages();
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
			System.exit(0);
		}

	}
}
