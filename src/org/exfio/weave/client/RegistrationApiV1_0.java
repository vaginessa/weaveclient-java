package org.exfio.weave.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.io.IOException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import org.json.simple.JSONObject;

import org.exfio.weave.WeaveException;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;
import org.exfio.weave.net.HttpException;
import org.exfio.weave.net.HttpClient;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.URIUtils;


public class RegistrationApiV1_0 extends AccountApi {
		
	private URI    baseURL;
	private String user;
	private String password;
		
	public RegistrationApiV1_0() throws WeaveException {
		super();
		this.version  = ApiVersion.v1_1;
		this.baseURL  = null;
		this.user     = null;
		this.password = null;
	}

	public void init(String baseURL, String user, String password) throws WeaveException {
		this.user     = user;
		this.password = password;

		try {
			this.baseURL  = new URI(baseURL);
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public boolean register(String baseURL, String user, String password, String email) throws WeaveException {
		Log.getInstance().debug("register()");
		
		try {
			this.baseURL  = new URI(baseURL);
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		}

		//Build registration URL
		URI location = this.baseURL.resolve(URIUtils.sanitize(String.format("/user/1.0/%s", user)));
				
		//Build HTTP request content
		JSONObject jobj = new JSONObject();
		jobj.put("password", password);
		jobj.put("email", email);
		
		//TODO - Support captcha
		jobj.put("captcha-challenge", "");
		jobj.put("captcha-response", "");
		
		HttpPut put = new HttpPut(location);
		CloseableHttpResponse response = null;

		try {
			//Backwards compatible with android version of org.apache.http
			StringEntity entityPut = new StringEntity(jobj.toJSONString());
			entityPut.setContentType("text/plain");
			entityPut.setContentEncoding("UTF-8");
			
			put.setEntity(entityPut);

			response = httpClient.execute(put);
			HttpClient.checkResponse(response);

		} catch (IOException e) {
			throw new WeaveException(e);
		} catch (HttpException e) {
			throw new WeaveException(e);
		} finally {
			HttpClient.closeResponse(response);
		} 

		init(baseURL, user, password);
		
		return true;
	}

	public URI getStorageUrl() throws WeaveException {

		URI storageURL = null;
		
		//TODO - confirm account exists, i.e. /user/1.0/USER returns 1
		
		URI location = this.baseURL.resolve(URIUtils.sanitize(String.format("/user/1.0/%s/node/weave", this.user)));
		HttpGet get = new HttpGet(location);
		CloseableHttpResponse response = null;

		try {
			response = httpClient.execute(get);
			HttpClient.checkResponse(response);
			
			storageURL = new URI(EntityUtils.toString(response.getEntity()));

		} catch (IOException e) {
			throw new WeaveException(e);
		} catch (HttpException e) {
			throw new WeaveException(e);
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		} finally {
			HttpClient.closeResponse(response);
		}
		
		return storageURL;
	}
	
}
