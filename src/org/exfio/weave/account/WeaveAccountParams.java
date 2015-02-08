package org.exfio.weave.account;

import lombok.Getter;

import org.exfio.weave.client.WeaveClientFactory.ApiVersion;

public class WeaveAccountParams {
	@Getter protected ApiVersion apiVersion = null;
	
	public String accountServer;
	public String user;
	public String password;
	public String email;
}
