package org.exfio.fxa;

import org.mozilla.gecko.background.fxa.FxAccountClient20.LoginResponse;


/**
 * FxA session JSON object
 * {
 *   "uid":"6fcc995b789b4a548197d5e734e58df6",
 *   "sessionToken":"bc24686560ad25bd763d495c660888c9ae4406d388cc113946ef669bad0e1d26",
 *   "keyFetchToken":"41188608a8af2955049a8bc5246e2ccd363af40af02f7a67027a290c0660a31a",
 *   "verified":true,
 *   "authAt":1422256742
 * }
 *
 */
public class FxAccountSession {
    public String remoteEmail;
    public String uid;
    public byte[] sessionToken;
    public boolean verified;
    public byte[] keyFetchToken;

    public FxAccountSession(LoginResponse response) {
    	fromLoginResponse(response);
    }

    public void fromLoginResponse(LoginResponse other) {
      this.remoteEmail   = other.remoteEmail;
      this.uid           = other.uid;
      this.verified      = other.verified;
      this.sessionToken  = other.sessionToken;
      this.keyFetchToken = other.keyFetchToken;
    }
  }

