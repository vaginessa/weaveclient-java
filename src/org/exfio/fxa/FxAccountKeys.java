package org.exfio.fxa;

public class FxAccountKeys {
	public final byte[] kA;
	public final byte[] kB;
	public final byte[] wrapkB;
	public FxAccountKeys(byte[] kA, byte[] kB, byte[] wrapkb) {
		this.kA = kA;
		this.kB = kB;
		this.wrapkB = wrapkb;
	}
}
