package org.exfio.fxa;

public class FxAccountKeys {
	public final byte[] kA;
	public final byte[] kB;
	public FxAccountKeys(byte[] kA, byte[] kB) {
		this.kA = kA;
		this.kB = kB;
	}
}
