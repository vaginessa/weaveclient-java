package org.exfio.weave.crypto;

import java.security.Provider;
import java.security.Security;

import org.exfio.weave.util.OSUtils;

public class JCEProvider {

	private static String cryptoProvider = null;
	
	public static String getCryptoProvider() {
		if ( cryptoProvider == null ) {
			String cryptoProviderClass = null;
			if ( OSUtils.isAndroid() ) {
				//On android we need to use Spongy Castle, i.e. SC
				cryptoProvider      = "SC";
				cryptoProviderClass = "org.spongycastle.jce.provider.BouncyCastleProvider";
			} else {
				cryptoProvider      = "BC";
				cryptoProviderClass = "org.bouncycastle.jce.provider.BouncyCastleProvider";
			}
			try {
				Class<?> provider = Class.forName(cryptoProviderClass);
				Security.addProvider((Provider)provider.newInstance());
			} catch (ClassNotFoundException e) {
				throw new AssertionError(e);
			} catch (IllegalAccessException e) {
				throw new AssertionError(e);
			} catch (InstantiationException  e) {
				throw new AssertionError(e);				
			}
		}
		return cryptoProvider;
	}

}
