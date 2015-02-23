weaveclient-java
================

Weave Sync/Firefox Sync client library written in Java.

## Features
* Compatible with Weave Sync Storage API v1.1 (pre Firefox v29) and v1.5 (FxA)
* Encrypt/Decrypt data stored on Weave Sync server (read and write)
* Commandline client

## Library

### Weave Client v1.1

```java
import org.exfio.weave.account.legacy.WeaveSyncV5AccountParams;
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.client.WeaveClientFactory;

WeaveSyncV5AccountParams weaveParams = new WeaveSyncV5AccountParams();
weaveParams.accountServer = "http://server/path/to/weave";
weaveParams.user          = "username";
weaveParams.password      = "really long password";
weaveParams.syncKey       = "CBGMDB56ISI5KVQWDIUB2K54HQ"; //Base32 encoded sync key

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

### Weave Client v1.5

```java
import org.exfio.weave.account.fxa.FxAccountParams;
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.client.WeaveClientFactory;

FxAccountParams weaveParams = new FxAccountParams();
weaveParams.accountServer = "http://server/path/to/fxa-account-server";
weaveParams.tokenServer   = "http://server/path/to/sync-token-server";
weaveParams.user          = "username";
weaveParams.password      = "really long password";

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

## Commandline

### Weave Client
```
Usage: weaveclient

 --plaintext                 do not encrypt/decrypt item
 -a,--account <arg>          load config for account
 -c,--collection <arg>       collection
 -d,--delete                 delete item. Requires -c and -i
 -f,--config-file <arg>      load config from file
 -h,--help                   print this message
 -i,--id <arg>               object ID
 -k,--sync-key <arg>         sync key
 -l,--log-level <arg>        set log level (trace|debug|info|warn|error)
 -m,--modify <arg>           update item with given value in JSONUtils
                             format. Requires -c and -i
 -n,--info                   get collection info. Requires -c
 -p,--password <arg>         password
 -s,--account-server <arg>   account server URL
 -t,--token-server <arg>     token server URL
 -u,--username <arg>         username
 -v,--api-version <arg>      api version (auto|1.1|1.5). Defaults to 1.1
```

### Weave Account
```
Usage: weaveaccount

 -a,--account <arg>            load config and database for account
 -e,--email <arg>              email
 -f,--config-file <arg>        load config from file
 -h,--help                     print this message
 -k,--synckey <arg>            synckey
 -l,--log-level <arg>          set log level (trace|debug|info|warn|error)
 -n,--create-account <arg>     create account
 -p,--password <arg>           password
 -r,--register-account <arg>   register existing account for this device
 -s,--account-server <arg>     account server URL
    --status                   account status
 -t,--token-server <arg>       token server URL
 -u,--username <arg>           username
 -v,--api-version <arg>        api version (auto|1.1|1.5). Defaults to 1.1
```
