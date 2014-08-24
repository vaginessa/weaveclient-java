package org.exfio.weave.ext.clientauth;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.lang.StringUtils;
import org.exfio.weave.WeaveException;
import org.exfio.weave.ext.comm.Client.EphemeralKey;
import org.exfio.weave.ext.comm.CommsStorage.ClientDataMapper;
import org.exfio.weave.ext.comm.CommsStorage.DefaultMessageDataMapper;
import org.exfio.weave.ext.comm.CommsStorage.PropertyDataMapper;
import org.exfio.weave.ext.comm.CommsStorage;
import org.exfio.weave.ext.comm.Message;
import org.exfio.weave.ext.comm.Message.EncodedMessage;
import org.exfio.weave.ext.comm.Message.MessageSession;
import org.exfio.weave.util.SQLUtils;

public class ClientAuthStorage {

	public static void initDB(Connection db) throws SQLException {
		initDB(db, false);
	}
	
	public static void initDB(Connection db, boolean force) throws SQLException{		
		ClientAuthSessionDataMapper.initDB(db, force);
		ClientAuthRequestMessageDataMapper.initDB(db, force);
		ClientAuthResponseMessageDataMapper.initDB(db, force);		
	}

	private static Message buildClientAuthMessage(Connection db, ResultSet rs, String messageType) throws SQLException, WeaveException {
		
		//TODO - delegate to factory class based on message type

		if ( messageType == "clientauthrequest" ) {
			return ClientAuthRequestMessageDataMapper.buildMessage(db, rs);			
		} else if ( messageType == "clientauthresponse" ) {
			return ClientAuthResponseMessageDataMapper.buildMessage(db, rs);			
		} else {
			throw new WeaveException(String.format("Message type '%s' not recognised", messageType));
		}
	}

	public static void createClientAuthMessage(Connection db, Message msg) throws SQLException, WeaveException {

		//TODO - delegate to factory class based on message type

		if ( msg.getMessageType() == "clientauthrequest" ) {
			ClientAuthRequestMessageDataMapper.createMessage(db, msg);
		} else if ( msg.getMessageType() == "clientauthresponse" ) {
			ClientAuthResponseMessageDataMapper.createMessage(db, msg);			
		} else {
			throw new WeaveException(String.format("Message type '%s' not recognised", msg.getMessageType()));
		}
	}

	public static ClientAuthSession getClientAuthSession(Connection db, String sessionId) throws SQLException, WeaveException {
		return getClientAuthSession(db, DefaultMessageDataMapper.getMessageSession(db, sessionId));
	}

	public static ClientAuthSession getClientAuthSession(Connection db, MessageSession session) throws SQLException, WeaveException {
		return ClientAuthSessionDataMapper.getClientAuthSession(db, session);
	}

	public static void createClientAuthSession(Connection db, ClientAuthSession session) throws SQLException, WeaveException {
		ClientAuthSessionDataMapper.createClientAuthSession(db, session);
	}

	public static void updateClientAuthSession(Connection db, ClientAuthSession session) throws SQLException, WeaveException {
		ClientAuthSessionDataMapper.updateClientAuthSession(db, session);
	}

	//-------------------------------------------
	// Data Mapper classes
	//-------------------------------------------

	public static class ClientAuthSessionDataMapper {

		public static void initDB(Connection db) throws SQLException {
			initDB(db, false);
		}
		
		public static void initDB(Connection db, boolean force) throws SQLException {

			String SQL = null;
			
			Statement st = db.createStatement();
			st.setQueryTimeout(CommsStorage.QUERY_TIMEOUT);
	
			if ( force ) st.executeUpdate("DROP TABLE IF EXISTS MessageSessionClientAuth");
			
		    SQL = "CREATE TABLE IF NOT EXISTS MessageSessionClientAuth "
		    	+ "("
	   		 	+ " MessageSessionID TEXT NOT NULL"
	   		 	+ " ,State TEXT NOT NULL"
		    	+ ")";
		    
		    st.executeUpdate(SQL);	
		}
		
		public static ClientAuthSession getClientAuthSession(Connection db, String sessionId) throws SQLException {
			return getClientAuthSession(db, DefaultMessageDataMapper.getMessageSession(db, sessionId));
		}
		
		public static ClientAuthSession getClientAuthSession(Connection db, MessageSession session) throws SQLException {

			ClientAuthSession caSession = new ClientAuthSession(session);
			
			String SQL = null;
			
		    //Create client auth session record
			SQL = "SELECT"
			    + " State"
				+ "FROM "
				+ " MessageSessionClientAuth "
				+ "WHERE "
				+ " MessageSessionID = ?";
	
			PreparedStatement st = db.prepareStatement(SQL);
			st.setQueryTimeout(CommsStorage.QUERY_TIMEOUT);	
			
			int col = 1;
			st.setString(col++, caSession.getSessionId());
	
			ResultSet rs = st.executeQuery();
			if ( !rs.next() ) {
				throw new SQLException(String.format("Couldn't load MessageSessionClientAuth record for MessageSessionID '%s'", session.getSessionId()));
			}

			caSession.setState(rs.getString("State"));
			
			return caSession;
		}

		public static void createClientAuthSession(Connection db, ClientAuthSession session) throws SQLException {

			//Create message session if it does not already exist
			if ( DefaultMessageDataMapper.getMessageSession(db, session.getSessionId()) == null ) {
				DefaultMessageDataMapper.createMessageSession(db, session);
			}
			
			String SQL = null;
			
		    //Create client auth session record
			SQL = "INSERT INTO MessageSessionClientAuth "
				+ "("
			    + " MessageSessionID"
			    + " ,State"
				+ ")"
				+ "VAULES(?, ?)";
	
			PreparedStatement st = db.prepareStatement(SQL);
			st.setQueryTimeout(CommsStorage.QUERY_TIMEOUT);	
			
			int col = 1;
			st.setString(col++, session.getSessionId());
			st.setString(col++, session.getState());
	
			st.executeUpdate();
		}

		public static void updateClientAuthSession(Connection db, ClientAuthSession session) throws SQLException {

			String SQL = null;
			
		    //Update client auth session record
			SQL = "UPDATE MessageSessionClientAuth "
			    + "SET"
			    + " State = ? "
			    + "WHERE"
			    + " MessageSessionID = ?";
	
			PreparedStatement st = db.prepareStatement(SQL);
			st.setQueryTimeout(CommsStorage.QUERY_TIMEOUT);	
			
			int col = 1;
			st.setString(col++, session.getState());
			st.setString(col++, session.getSessionId());
	
			st.executeUpdate();
		}

	}

	public static class ClientAuthRequestMessageDataMapper extends DefaultMessageDataMapper {

		public static void initDB(Connection db) throws SQLException {
			initDB(db, false);
		}
		
		public static void initDB(Connection db, boolean force) throws SQLException {

			String SQL = null;
			
			Statement st = db.createStatement();
			st.setQueryTimeout(CommsStorage.QUERY_TIMEOUT);
	
			if ( force ) st.executeUpdate("DROP TABLE IF EXISTS MessageClientAuthRequest");
			
		    SQL = "CREATE TABLE IF NOT EXISTS MessageClientAuthRequest "
		    	+ "("
		    	+ " MessageID INTEGER NOT NULL"
	   		 	+ " ,ClientID TEXT NOT NULL"
	   		 	+ " ,ClientName TEXT NOT NULL"
	   		 	+ " ,Auth TEXT NOT NULL"
		    	+ ")";
		    
		    st.executeUpdate(SQL);	
		}
		
		protected static Message buildMessage(Connection db, ResultSet rs) throws SQLException {
			
			Message msgBase = DefaultMessageDataMapper.buildMessage(db, rs);
			ClientAuthRequestMessage msg = null;
			
			try {
				msg = new ClientAuthRequestMessage(msgBase.getEncodedMessage());
			} catch (WeaveException e) {
				
			}

			/*
			String SQL = null;
			
			//Get client auth request specific data
			SQL = "SELECT"
			    + " MessageID"
			    + " ,ClientID"
			    + " ,ClientName"
			    + " ,Auth"
				+ "FROM"
				+ " MessageClientAuthRequest"
				+ "WHERE"
				+ " MessageID = ?";

			PreparedStatement st = db.prepareStatement(SQL);
			st.setQueryTimeout(CommsStorage.QUERY_TIMEOUT);
			int col = 1;
			st.setInt(col++, msg.getMessageId());
			
			ResultSet rs2 = st.executeQuery();
			if ( !rs2.next() ) {
				throw new SQLException(String.format("Couldn't load MessageClientAuthRequest record for MessageID '%s'", msg.getMessageId()));
			}

			
			msg.setClientId(rs.getString("ClientID"));
			msg.setClientName(rs.getString("ClientName"));
			msg.setAuth(rs.getString("Auth"));
			*/
			
			return msg;
			
		}
		
		public static void createMessage(Connection db, Message msg) throws SQLException {

			/*
			DefaultMessageDataMapper.createMessage(db, msg.getEncodedMessage());
			
			ClientAuthRequestMessage caMsg = (ClientAuthRequestMessage)msg;
			
			String SQL = null;
			
		    //Create message record
			SQL = "INSERT INTO MessageClientAuthRequest "
				+ "("
			    + " MessageID"
			    + " ,ClientID"
			    + " ,ClientName"
			    + " ,Auth"
				+ ")"
				+ "VAULES(?, ?, ?, ?)";
	
			PreparedStatement st = db.prepareStatement(SQL);
			st.setQueryTimeout(CommsStorage.QUERY_TIMEOUT);	
			
			int col = 1;
			st.setInt   (col++, caMsg.getMessageId());
			st.setString(col++, caMsg.getClientId());
			st.setString(col++, caMsg.getClientName());
			st.setString(col++, caMsg.getAuthAsString());
	
			st.executeUpdate();
			*/
		}
	}

	public static class ClientAuthResponseMessageDataMapper extends DefaultMessageDataMapper {
	
		public static void initDB(Connection db) throws SQLException {
			initDB(db, false);
		}
		
		public static void initDB(Connection db, boolean force) throws SQLException {

			String SQL = null;
			
			Statement st = db.createStatement();
			st.setQueryTimeout(CommsStorage.QUERY_TIMEOUT);
	
			if ( force ) st.executeUpdate("DROP TABLE IF EXISTS MessageClientAuthResponse");
			
		    SQL = "CREATE TABLE IF NOT EXISTS MessageClientAuthResponse "
		    	+ "("
		    	+ " MessageID INTEGER NOT NULL"
		    	+ " ,ClientID TEXT NOT NULL"
		    	+ " ,ClientName TEXT NOT NULL"
		    	+ " ,Status TEXT NOT NULL"
		    	+ " ,ClientAuthReqMessage TEXT"
		    	+ " ,SyncKey TEXT"
		    	+ ")";
		    
		    st.executeUpdate(SQL);	
		}		

		protected static Message buildMessage(Connection db, ResultSet rs) throws SQLException {
			
			ClientAuthResponseMessage msg = new ClientAuthResponseMessage();
			
			DefaultMessageDataMapper.buildMessage(db, rs, msg.getEncodedMessage());
			
			String SQL = null;
			
			//Get client auth response specific data
			SQL = "SELECT"
			    + " MessageID"
			    + " ,ClientID"
			    + " ,ClientName"
		    	+ " ,Status"
		    	+ " ,ClientAuthReqMessage"
		    	+ " ,SyncKey"
				+ "FROM"
				+ " MessageClientAuthResponse"
				+ "WHERE"
				+ " MessageID = ?";

			PreparedStatement st = db.prepareStatement(SQL);
			st.setQueryTimeout(CommsStorage.QUERY_TIMEOUT);
			int col = 1;
			st.setInt(col++, msg.getMessageId());
			
			ResultSet rs2 = st.executeQuery();
			if ( !rs2.next() ) {
				throw new SQLException(String.format("Couldn't load MessageClientAuthResponse record for MessageID '%s'", msg.getMessageId()));
			}

			msg.setClientId(rs.getString("ClientID"));
			msg.setClientName(rs.getString("ClientName"));
			msg.setStatus(rs.getString("Status"));
			msg.setMessage(rs.getString("ClientAuthReqMessage"));
			msg.setSyncKey(rs.getString("SyncKey"));
			
			return msg;
		}
		
		public static void createMessage(Connection db, Message msg) throws SQLException {

			DefaultMessageDataMapper.createMessage(db, msg.getEncodedMessage());
			
			ClientAuthResponseMessage caMsg = (ClientAuthResponseMessage)msg;
			
			String SQL = null;
			
		    //Create message record
			SQL = "INSERT INTO MessageClientAuthRequest "
				+ "("
			    + " MessageID"
			    + " ,ClientID"
			    + " ,ClientName"
		    	+ " ,Status"
		    	+ " ,ClientAuthReqMessage"
		    	+ " ,SyncKey"
				+ ")"
				+ "VAULES(?, ?, ?, ?)";
	
			PreparedStatement st = db.prepareStatement(SQL);
			st.setQueryTimeout(CommsStorage.QUERY_TIMEOUT);	
			
			int col = 1;
			st.setInt   (col++, caMsg.getMessageId());
			st.setString(col++, caMsg.getClientId());
			st.setString(col++, caMsg.getClientName());
			st.setString(col++, caMsg.getStatus());
			st.setString(col++, caMsg.getMessage());
			st.setString(col++, caMsg.getSyncKey());
	
			st.executeUpdate();
		}
	}

}
