package org.exfio.weave.util;

import org.apache.commons.logging.LogFactory;

public class Log {

	protected static final String logtag = "weaveclient";
	private static boolean initLog = false;
	
	public static void init(String level) {
		initLog = true;
	
		//Initialise Apache commons logging
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
		System.setProperty("org.apache.commons.logging.simplelog.showlogname", "true");
		System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
		System.setProperty("org.apache.commons.logging.simplelog.defaultlog", level);

		//Explicitly set log level for our default logger 
		System.setProperty("org.apache.commons.logging.simplelog.log." + logtag, level);

		//Enable http logging if level is debug or trace
		if ( level.toLowerCase().matches("debug|trace") ) {			
			System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "debug");
			System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.headers", "debug");
		}		
	}

	public static org.apache.commons.logging.Log getInstance() {
		return getInstance(logtag);
	}

	public static org.apache.commons.logging.Log getInstance(String context) {
		if ( !initLog ) {
			System.err.println("Log not initialised, setting default log level to warn");
			init("warn");
		}	
		return LogFactory.getLog(context);
	}

	public static void setLogLevel(String level) {
		setLogLevel(logtag, level);
	}
	
	public static void setLogLevel(String logger, String level) {
		if ( !initLog ) {
			System.err.println("Log not initialised, setting default log level to warn");
			init("warn");
		}
		System.setProperty("org.apache.commons.logging.simplelog.log." + logger, level);
	}

}
