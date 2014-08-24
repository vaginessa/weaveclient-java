package org.exfio.weave.ext.clientauth;

import lombok.Data;
import lombok.EqualsAndHashCode;

import org.exfio.weave.ext.comm.Message.MessageSession;

@Data
@EqualsAndHashCode(callSuper=true)
public class ClientAuthSession extends MessageSession {
	
	private String state;

	public ClientAuthSession(MessageSession session) {
		super(session);
		this.state = null;
	}
}
