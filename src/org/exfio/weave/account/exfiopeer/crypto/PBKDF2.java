package org.exfio.weave.account.exfiopeer.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.exfio.weave.util.Base64;

public class PBKDF2 {

	public byte[] generatePBKDF2Salt(int size) {
		SecureRandom rnd = new SecureRandom();
        return rnd.generateSeed(size);
	}
	
	public String generatePBKDF2Digest(String cleartext, byte[] salt, int iterations, int length) {
		
		byte[] digestBin = null;
		
		try {
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			KeySpec spec = new PBEKeySpec(cleartext.toCharArray(), salt, iterations, length);
			digestBin = factory.generateSecret(spec).getEncoded();
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		} catch (InvalidKeySpecException e) {
			throw new AssertionError(e);
		}
		
		return Base64.encodeBase64String(digestBin);

		//PKCS5S2ParametersGenerator generator = new PKCS5S2ParametersGenerator(new SHA256Digest());
		//generator.init(PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(password), salt, iterations);
		//KeyParameter key = (KeyParameter)generator.generateDerivedMacParameters(keySizeInBits);
	}

}
