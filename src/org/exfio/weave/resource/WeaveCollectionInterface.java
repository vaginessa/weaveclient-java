package org.exfio.weave.resource;

import at.bitfire.davdroid.resource.Resource;

import org.exfio.weave.WeaveException;

public interface WeaveCollectionInterface {

	public String[] getObjectIds();

	public String[] getObjectIdsModifiedSince(Float modifedDate);
	
	public abstract Resource[] multiGet(String[] ids) throws WeaveException;
	
	public abstract Resource get(String id) throws WeaveException;
	
	public abstract void add(Resource res) throws WeaveException;
	
	public abstract void update(Resource res) throws WeaveException;
	
	public abstract void delete(String id) throws WeaveException;	
	
}
