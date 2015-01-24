package org.exfio.weave.util;

import java.io.IOException;
import java.net.UnknownHostException;
import java.net.InetAddress;

import org.apache.commons.io.IOUtils;

public class OSUtils {
	 
	private static String os      = "unknown";
	private static String distro  = "unknown";
	private static String version = "unknown";
	private static String type    = "unknown"; //desktop or mobile
 
	static {
		init();
	}
	
	private static void init() {
		String osName  = System.getProperty("os.name").toLowerCase();
		String vm      = System.getProperty("java.vm.name").toLowerCase();
		String runtime = System.getProperty("java.runtime.name").toLowerCase();
		
		if ( osName.indexOf("win") >= 0 ) {
			os = "windows";
			type = "desktop";
		} else if ( osName.indexOf("mac") >= 0 ) {
			os = "mac";
			
			if ( osName.indexOf("os x") >= 0 || osName.indexOf("osx") >= 0 ) {
				distro = "osx";
			}
			type = "desktop";
		} else if ( osName.indexOf("linux") >= 0 ) {
			os = "linux";
			
			if ( runtime.indexOf("android") >= 0 || vm.indexOf("davlik") >= 0 ) {
				distro = "android";
				type = "mobile";
			} else {
				type = "desktop";
			}
		} else if ( osName.indexOf("nix") >= 0 || osName.indexOf("aix") >= 0 ) {
			os = "unix";
			type = "desktop";
		} else if ( osName.indexOf("sunos") >= 0 ) {
			os = "unix";
			distro = "solaris";
			type = "desktop";
		}		
	}
	
	public static String getHostName() {
		
		String hostname = null;

		if ( isWindows() ) {
			// On Windows 'COMPUTERNAME' should be set
			hostname = System.getenv("COMPUTERNAME");
		} else if ( isUnix() || isLinux() ) {
			//On Unix and Linux HOSTNAME should be set
			hostname = System.getenv("HOSTNAME");
		} else {
			try {
				hostname = InetAddress.getLocalHost().getHostName();
			} catch (UnknownHostException e) {
				Log.getInstance().warn("Couldn't get hostname - " + e.getMessage());
			}
		}
		return hostname;
	}

	public static String getPrettyName() {
		
		String prettyname = getHostName();

		if ( isWindows() ) {
			//TODO - On Windows use net config server
			/*
			c:\> net config server

			Server Name                           \\win2008tst
			Server Comment                        My Super Fast Server

			Software version                      Windows Server 2008 R2 Enterprise

			Server is active on
			NetbiosSmb (win2008tst)

			Server hidden                         No
			Maximum Logged On Users               16777216
			Maximum open files per session        16384

			Idle session time (min)               15
			The command completed successfully.
			*/
		} else if ( isOSX() ) {
			//On OSX use scuitl --get ComputerName
			try {
				Process proc = Runtime.getRuntime().exec("scutil --get ComputerName");
				prettyname = IOUtils.toString(proc.getInputStream());
			} catch (IOException e) {
				Log.getInstance().warn("Couldn't get pretty name - " + e.getMessage());
			}
		} else if ( isAndroid() ) {
			//On Android get Bluetooth device name
			try {
				prettyname = AndroidCompat.getPrettyName();
			} catch (NoSuchMethodException e) {
				Log.getInstance().warn("Couldn't get pretty name - " + e.getMessage());
			}
		}
				
		return prettyname;
	}

	public static String getOS() {
		return os;
	}

	public static String getDistro() {
		return distro;
	}

	public static String getVersion() {
		return version;
	}

	public static String getType() {
		return type;
	}
	
	public static boolean isWindows() { 
		return ( os.equals("windows") );
	}
 
	public static boolean isMac() {
		return ( os.equals("mac") );
	}

	public static boolean isUnix() {
		return ( os.equals("unix") );
	}

	public static boolean isLinux() {
		return ( os.equals("linux") );
	}

	public static boolean isOSX() {
		return ( os.equals("mac") && distro.equals("osx") );
	}

	public static boolean isAndroid() {
		return ( os.equals("linux") && distro.equals("android") );
	}
	
	public static boolean isDesktop() {
		return getType().equals("desktop");
	}

	public static boolean isMobile() {
		return getType().equals("mobile");
	}
}
