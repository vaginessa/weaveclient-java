package org.exfio.weave.storage;

import lombok.Getter;
import lombok.ToString;

@ToString
public class WeaveCollectionInfo {
	@Getter protected String  name;
	@Getter protected Double  modified;
	@Getter protected Long    count;
	@Getter protected Double  usage;
		
	public WeaveCollectionInfo(String name) {
		this.name = name;
	}

	public WeaveCollectionInfo(String name, Double modified, Long count, Double usage) {
		this.name     = name;
		this.modified = modified;
		this.count    = count;
		this.usage    = usage;
	}	
}
