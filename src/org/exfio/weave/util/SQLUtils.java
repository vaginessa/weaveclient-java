package org.exfio.weave.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.lang.StringEscapeUtils;

public class SQLUtils {
	
	public static String quote(String value) {
		return String.format("'%s'", StringEscapeUtils.escapeSql(value));
	}

	public static String quote(int id) {
		return "" + id;
	}

	public static String quoteArray(String[] valueArray) {
		String quoted    = "";
		String separator = "";
		for (int i = 0; i < valueArray.length; i++) {
			quoted += separator + quote(valueArray[i]);
			separator = ", ";
		}
		return quoted;
	}


	public static String like(String value) {
		return like(value, true, true);
	}
	
	public static String like(String value, boolean matchBefore, boolean matchAfter) {
		String like = value;
		like = like.replaceAll("\\", "\\\\");
		like = like.replaceAll("%", "\\%");
		like = like.replaceAll("_", "\\_");
		
		if ( matchBefore ) like = "%" + like;
		if ( matchAfter  ) like = like + "%";
		
		return String.format("%s ESCAPE '\\'", quote(like));
	}
	
	public static Date sqliteDatetime(String datetime) {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
		Date dt = null;
		try {
		    dt = df.parse(datetime);
		} catch (ParseException e) {
		   throw new AssertionError(e.getMessage());
		}
		return dt;
	}
}
