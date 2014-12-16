weaveclient-java
================

Weave Sync/Firefox Sync client library written in Java.

## Features
* Compatible with Weave Sync v5 (pre Firefox 29)
* Encrypt/Decrypt data stored on Weave Sync server (read and write)
* Commandline client

## Library

### Weave Client

```java
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.client.WeaveClientV5Params;
import org.exfio.weave.client.WeaveClientFactory;

WeaveClientV5Params weaveParams = new WeaveClientV5Params();
weaveParams.baseURL  = "http://server/path/to/weave";
weaveParams.user     = "username";
weaveParams.password = "really long password";
weaveParams.syncKey  = "CBGMDB56ISI5KVQWDIUB2K54HQ"; //Base32 encoded sync key

WeaveClient weaveClient = null;
	
try {
    weaveClient = WeaveClientFactory.getInstance(weaveParams);
} catch (Exception e) {
    //Handle error
}

String collection = "bookmarks";

WeaveBasicObject[] colWbo = weaveClient.getCollection(collection, null, null, null, null, null, null, null, null, null, true);
for (var i = 0; i < colWbo.length; i++) {
    System.out.print(colWbo[i].payload + "\n");
}

String id = "FprxRkbQsyKe" #Base64 encoded object id (unique within collection)
WeaveBasicObject wbo = weaveClient.get(collection, id, true);
System.out.print(col.payload + "\n");
```

### Weave Auth

```java
import org.exfio.weave.ext.clientauth.ClientAuth;

//Request client auth
String clientName = "My client";
String database   = "path/to/client/database";

try {
  	ClientAuth auth = new ClientAuth(weaveClient);
		auth.requestClientAuth(clientName, password, database);
} catch(Exception e) {
    //Handle error
}

System.out.print("Client auth request pending with auth code " + auth.getAuthCode() + "\n");
```

## Commandline

### Weave Client
```
Usage: weaveclient

 -a,--account <arg>           load config for account
 -c,--collection <arg>        collection
 -d,--delete                  delete item. Requires -c and -i
 -e,--email <arg>             email
 -f,--config-file <arg>       load config from file
 -h,--help                    print this message
 -i,--id <arg>                object ID
 -k,--sync-key <arg>          sync key (required for storage v5)
 -l,--log-level <arg>         set log level (trace|debug|info|warn|error)
 -m,--modify <arg>            update item with given value in JSONUtils format. Requires -c and -i
 -n,--info                    get collection info. Requires -c
 -p,--password <arg>          password
 -r,--register <arg>          register
 -s,--server <arg>            server URL
 -t,--plaintext               do not encrypt/decrypt item
 -u,--username <arg>          username
 -v,--storage-version <arg>   storage version (auto|5). Defaults to auto
```

### Weave Auth
```
Usage: weaveauth

 -a,--account <arg>         load config and database for account
 -c,--auth-code <arg>       verification code for client authorisation
 -d,--database-file <arg>   load database from file
 -f,--config-file <arg>     load config from file
 -h,--help                  print this message
 -i,--auth-init <arg>       reset client authorisation. WARNING all clients will need to re-authenticate
 -j,--auth-client <arg>     request client authorisation
 -l,--log-level <arg>       set log level (trace|debug|info|warn|error)
 -m,--messages              check for new messages
 -o,--auth-approve <arg>    approve client authorisation request
 -p,--password <arg>        password
 -x,--auth-reject <arg>     reject client authorisation request
```
