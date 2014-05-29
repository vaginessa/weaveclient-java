package org.exfio.weave.client;

import java.io.IOException;
import java.net.URI;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.exfio.weave.Log;

import org.exfio.weave.WeaveException;
import org.exfio.weave.client.WeaveStorageContext;
import org.exfio.weave.client.WeaveStorageV5;

public class WeaveClient {

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
	
	public WeaveBasicObject decryptWeaveBasicObject(WeaveBasicObject wbo) throws WeaveException {
		return decryptWeaveBasicObject(wbo, null);
	}
	public WeaveBasicObject decryptWeaveBasicObject(WeaveBasicObject wbo, String keyLabel) throws WeaveException {
		return ws.decryptWeaveBasicObject(wbo, keyLabel);
	}

	public WeaveBasicObject getItem(String collection, String id) throws WeaveException {
		return ws.get(collection, id);
	}

	public WeaveCollectionInfo getCollectionInfo(String collection) throws WeaveException {
		return ws.getCollectionInfo(collection, true, true);
	}

	public String[] getCollectionIds(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort) throws WeaveException {
		return ws.getCollectionIds(collection, ids, older, newer, index_above, index_below, limit, offset, sort);
	}

	public WeaveBasicObject[] getCollection(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort, String format) throws WeaveException {
		return ws.getCollection(collection, ids, older, newer, index_above, index_below, limit, offset, sort, format);
	}

	public Double putItem(String collection, String id, WeaveBasicObject wbo) throws WeaveException {
		return ws.put(collection, id, wbo);
	}

	public void deleteItem(String collection, String id) throws WeaveException {
		ws.delete(collection, id);
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
		String collection = null;
		String id         = null;
		String payload    = null;
		boolean delete    = false;
		boolean info      = false;
		String loglevel   = null;
		
		WeaveClient.StorageVersion storageVersion = null;
		
		// Parse commandline arguments
		Options options = new Options();
		
		options.addOption("h", "help", false, "print this message");
		options.addOption("s", "server", true, "server URL");
		options.addOption("u", "username", true, "username");
		options.addOption("p", "password", true, "password");
		options.addOption("v", "storage-version", true, "storage version (default 5)");		
		options.addOption("k", "sync-key", true, "sync key (required for storage v5)");
		options.addOption("c", "collection", true, "collection");
		options.addOption("i", "id", true, "object ID");
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
		
		if ( !(cmd.hasOption('s') && cmd.hasOption('u') && cmd.hasOption('p')) ) {
			System.err.println("server, username and password are required parameters");
			System.exit(1);
		}

		//Set host and credential details
		baseURL  = cmd.getOptionValue('s');
		username = cmd.getOptionValue('u');
		password = cmd.getOptionValue('p');

		//Validate URI syntax
		try {
			URI.create(baseURL);
		} catch (IllegalArgumentException e) {
			System.err.printf("'%s' is not a valid URI, i.e. should be http(s)://example.com\n", baseURL);
			System.exit(1);
		}			

		//Set storage version
		if ( cmd.hasOption('v') ) {
			if ( cmd.getOptionValue('v') == "5" ) {
				storageVersion = WeaveClient.StorageVersion.v5;			
			}
		} else {
			storageVersion = WeaveClient.StorageVersion.v5;			
		}
		if ( storageVersion == WeaveClient.StorageVersion.v5 ){
			//Only v5 is currently supported
			if ( !cmd.hasOption('k') ) {
				System.err.println("sync-key is a required parameter for storage version 5");
				System.exit(1);
			}
			synckey = cmd.getOptionValue('k');
		}		
		
		//Set collection
		if ( !cmd.hasOption('c') ) {
			System.err.println("collection is a required parameter");
			System.exit(1);
		}
		collection = cmd.getOptionValue('c');

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

		//Need to set log level BEFORE instansiating Logger
		loglevel = "warn";
		if ( cmd.hasOption('l') ) {
			loglevel = cmd.getOptionValue('l').toLowerCase();
			if ( !loglevel.matches("trace|debug|info|warn|error") ) {
				System.err.println("log level must be one of (trace|debug|info|warn|error)");
				System.exit(1);		
			}
		}
		Log.setLogLevel(loglevel);
		
		Log.getInstance().debug(cmd.getArgList().toString());

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
				modified = weaveClient.putItem(collection, id, wbo);
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			System.out.println(String.format("modified: %f", modified));

		} else if ( delete ) {
			
			try {
				weaveClient.deleteItem(collection, id);
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
					WeaveBasicObject wbo = weaveClient.getItem(collection, id);
					System.out.print(wbo.getPayload());
				} else {
					WeaveBasicObject[] colWbo = weaveClient.getCollection(collection, null, null, null, null, null, null, null, null, null);
					for (int i = 0; i < colWbo.length; i++) {
						System.out.println(colWbo[i].getPayload().trim());
					}	
				}
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
		}
	}
}
