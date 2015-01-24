package org.exfio.weave.account;

import lombok.Getter;
import lombok.Setter;

import org.exfio.weave.client.WeaveClientRegistrationParams;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;
import org.exfio.weave.client.WeaveClientFactory.StorageVersion;

public class WeaveAccountParams {
	@Getter protected ApiVersion apiVersion = null;
	@Getter protected StorageVersion storageVersion = null;
	@Getter @Setter protected WeaveClientRegistrationParams registrationParams;
	
	public String accountServer;
	public String user;
	public String password;
	public String email;
}
