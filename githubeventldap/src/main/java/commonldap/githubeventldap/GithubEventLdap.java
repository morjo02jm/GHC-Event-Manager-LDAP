package commonldap.githubeventldap;

import org.json.*;

import java.sql.*;

import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.*;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;

import javax.naming.*;
import javax.naming.directory.*;

//import org.eclipse.core.runtime.*;

//import junit.framework.*;

import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;

import commonldap.commonldap.CommonLdap;
import commonldap.commonldap.JCaContainer;
import commonldap.commonldap.JCaData;
import commonldap.commonldap.SDTicket;


//Main Class
@SuppressWarnings("unused")

public class GithubEventLdap {
	static int iReturnCode = 0;
	
	static String sBCC = "";
	static CommonLdap frame;
	static String sAccessToken = "";
	static String sAPI = "";
	static String sLocation = "";
	static String sAuthFile = "";
	static JCaContainer cUserInfo = new JCaContainer();
	static JCaContainer cAuthorizations = new JCaContainer();
	static JCaContainer cOrg = new JCaContainer();
	static DateFormat dateFormat;
	
	// Notification
	static String tagUL = "<ul> ";

	GithubEventLdap()
	{
		// Leaving empty
	}

	private static boolean isOrgTracked(String tOrg) {
		if (tOrg.equalsIgnoreCase("CASaaSOps")||
			tOrg.equalsIgnoreCase("RallySoftware") ||
			tOrg.equalsIgnoreCase("RallyCommunity") ||
			tOrg.equalsIgnoreCase("RallyTools") ||
			tOrg.equalsIgnoreCase("RallyApps") ||
			tOrg.equalsIgnoreCase("flowdock") ||
			tOrg.equalsIgnoreCase("CATechnologies") ||
			tOrg.equalsIgnoreCase("waffleio") ||
			tOrg.equalsIgnoreCase("Blazemeter")) 
		{
			return true;
		}
		return false;
	}; //doesOrgBelongToCA
	
	private static String lookupCorporateID(String sGithubID, JCaContainer cUserInfo, String sType) {
		String sCorpID = sGithubID;
		
		int[] aID = cUserInfo.find("login", sGithubID);
		if (aID.length > 0) {
			sCorpID = cUserInfo.getString((sType=="ghe"?"raw_login":"pmfkey"), aID[0]);
		}
		return sCorpID;
	} // lookupCorporateID
	
	private static void readUnprocessedEvents(JCaContainer cEvents, String sImagDBPassword, String sType) 
	{
		PreparedStatement pstmt = null; 
		String sqlStmt;
		int iIndex = 0;
		ResultSet rSet;
		
		String sqlError = "SQLServer. Unable to connect to IMAG Database.";
		String sJDBC = "jdbc:sqlserver://AWS-UQAPA6ZZ:1433;databaseName=GMQARITCGISTOOLS;user=gm_tools_user;password="+sImagDBPassword+";";
		try {
			Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
			Connection conn = DriverManager.getConnection(sJDBC);

			String sApp, sAppLoc;
			switch (sType) {
				case "ghe":
				default: 
				sApp = "GitHub";
				sAppLoc = "github-isl-01.ca.com";
				break;
			case "github.com":
				sApp = "GitHub";
				sAppLoc = "github.com";
				break;				
			}

			sqlError = "SQLServer. Error reading webhook event records from IMAG AUDIT Database.";
			sqlStmt = "select * from GITHUB_EVENTS where ProcessTime IS NULL AND ApplicationLocation=\'"+sAppLoc+"\'";
			pstmt=conn.prepareStatement(sqlStmt); 
			rSet = pstmt.executeQuery();

			while (rSet.next()) {		
				cEvents.setString("ApplicationLocation", rSet.getString("ApplicationLocation").trim(), iIndex);
				cEvents.setString("ResourceOwner1",      rSet.getString("ResourceOwner1").trim(), iIndex);
				cEvents.setString("ResourceOwner2",      rSet.getString("ResourceOwner2").trim(), iIndex);
				cEvents.setString("ResourceName",        rSet.getString("ResourceName").trim(), iIndex);
				cEvents.setString("EventAttributes",     rSet.getString("EventAttributes").trim(), iIndex);
				cEvents.setString("User_ID",             rSet.getString("User_ID").trim(), iIndex);			
				iIndex++;
			} // loop over record sets

			if (iIndex>0) {
				frame.printLog(dateFormat.format(new Date())+":"+iIndex+" Event Records Read.");	
				sqlError = "SQLServer. Error marking webhook events as processed.";
				sqlStmt = "update GITHUB_EVENTS set ProcessTime=GetUTCDate() where ProcessTime IS NULL and ApplicationLocation='"+sAppLoc+"'";
				pstmt=conn.prepareStatement(sqlStmt); 
				int iResult = pstmt.executeUpdate();
				if (iResult == 0) {
					frame.printErr(dateFormat.format(new Date())+":"+sqlError);
				}
			}
			conn.close();

		} catch (ClassNotFoundException e) {
			iReturnCode = 101;
			frame.printErr(e.getLocalizedMessage());			
			System.exit(iReturnCode);
		} catch (SQLException e) {     
			iReturnCode = 102;
			frame.printErr(sqlError);
			frame.printErr(e.getLocalizedMessage());			
			System.exit(iReturnCode);
		}	
	} //readUnprocessedEvents
	
	private static boolean processPolicyOnPublicizedRepositories(String sOrg, String sRepo) {
		String sURL = "https://"+ sAPI + "/repos/"+ sOrg + "/"+sRepo+"?access_token="+sAccessToken+"&&per_page=1000";
		try {	
			JSONObject json = frame.readJsonFromUrl(sURL);
			boolean isPrivate = json.getBoolean("private");
			if (isPrivate) return false;
			
			//Check if the repository is authorized to be public
			for (int iIndex=0; iIndex<cAuthorizations.getKeyElementCount("policy"); iIndex++) {
				String aLocation = cAuthorizations.getString("location", iIndex);
				String aPolicy   = cAuthorizations.getString("policy", iIndex);
				String aOrg      = cAuthorizations.getString("organization", iIndex);
				String aRepo     = cAuthorizations.getString("repository", iIndex);
				String aUser     = cAuthorizations.getString("user", iIndex);
				
				if (aLocation.equalsIgnoreCase(sLocation) &&
					aPolicy.equalsIgnoreCase("public") &&
					aOrg.equalsIgnoreCase(sOrg) &&
					(aRepo.equalsIgnoreCase(sRepo) || aRepo.equalsIgnoreCase("***all***")) &&
					aUser.equalsIgnoreCase("***all***")) {
						return false;
				}
			}
			
			//Run a curl command to change the repository status to private
			String sMessage = "Making "+sLocation+" repository, "+sRepo+", in organization, "+sOrg+", private.";
			String sCommand = "curl -X PATCH -d \" { \\\"private\\\": true } \" -H \"Authorization: token "+sAccessToken+"\"  https://"+sAPI+"/repos/"+sOrg+"/"+sRepo;
			
			Process p = Runtime.getRuntime().exec(sCommand);
	        BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));	
	        //BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
	        
	        // read the output from the command
	        frame.printLog(">>>"+sMessage);
	        String s;
	        while ((s = stdInput.readLine()) != null) {
	        	frame.printLog(s);
	        }	
			
		}
		catch (IOException e) {
			iReturnCode = 201;
		    frame.printErr("Couldn't read JSON Object from: "+e.getLocalizedMessage());	
		    frame.printErr(e.getStackTrace().toString());
		    System.exit(iReturnCode);						
		}
		catch (JSONException e) {						
			iReturnCode = 202;
		    frame.printErr("Couldn't read JSON Object from: "+e.getLocalizedMessage());			
		    frame.printErr(e.getStackTrace().toString());
		    System.exit(iReturnCode);						
		}									
		
		return true;
	} //processPolicyOnPublicizedRepositories
	
	private static boolean processPolicyOnForkedRepositoriesByUser(String sOrg, String sRepo, String sCorpID) {
		return true;
	} //processPolicyOnForkedRepositoriesByUser
	
	
	
	// main routine
	public static void main(String[] args)
	{
		int iParms = args.length;
		int iReturnCode = 0;
		boolean bProcessChanges = true;
		boolean bLoadOnly = false;
		
		String sMapFile = "";
		String sBCC = "";
		String sLogPath = "githubeventldap.log";
				
		String sGitHubAccessToken = "";
		String sGitHubComAccessToken = "";
		String sImagDBPassword = "";
		String sApp, sAppLocation;
		String sType = "github.com";

    	dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    	
		// check parameters
		for (int i = 0; i < iParms; i++)
		{					
			if (args[i].compareToIgnoreCase("-github.com") == 0 )
			{
				sType = "github.com";
			}	
			else if (args[i].compareToIgnoreCase("-ghe") == 0 )
			{
				sType = "ghe";
			}	
			else if (args[i].compareToIgnoreCase("-mapfile") == 0 )
			{
				sMapFile = args[++i];
			}			
			else if (args[i].compareToIgnoreCase("-authfile") == 0 )
			{
				sAuthFile = args[++i];
			}			
			else if (args[i].compareToIgnoreCase("-bcc") == 0 )
			{
				sBCC = args[++i];
			}			
			else if (args[i].compareToIgnoreCase("-log") == 0 )
			{
				sLogPath = args[++i];
			}	
			else {
				frame.printLog("Argument: "+args[i]);
				frame.printLog("Usage: githubeventldap [-ghe | -github.com -mapfile textfile ][-bcc emailaddress] [-log textfile] [-h |-?]");
				frame.printLog(" -github.com (default) option indicates checking github.com webhook events");
				frame.printLog(" -ghe option indicates checking GHE webhook events");
				frame.printLog(" -bcc option specifies an email address to bcc on notifications sent to users");
				frame.printLog(" -log option specifies location log file.");
				System.exit(iReturnCode);
			}
		} // end for
		
		JCaContainer cLDAP = new JCaContainer();
		frame = new CommonLdap("githubeventldap",
        		               sLogPath,
        		               sBCC,
        		               cLDAP);

		try {
			Map<String, String> environ = System.getenv();
	        for (String envName : environ.keySet()) {
	        	if (envName.equalsIgnoreCase("GITHUB_ACCESS_TOKEN"))     
	        		sGitHubAccessToken = environ.get(envName);
	        	if (envName.equalsIgnoreCase("GITHUBCOM_ACCESS_TOKEN"))  
	        		sGitHubComAccessToken = environ.get(envName);
	        	if (envName.equalsIgnoreCase("IMAG_DB_PASSWORD"))        
	        		sImagDBPassword = frame.AESDecrypt(environ.get(envName));
	        }
	        
	        
			if (!sMapFile.isEmpty()) {
		        switch (sType) {
		        case "github.com":
		        default:
					frame.readInputListGeneric(cUserInfo, sMapFile, ',');
		        	break;
	        	
		        case "ghe":
					String[] aColumnList = { "login", "raw_login" };
					frame.readInputListGenericWithColumnList(cUserInfo, sMapFile, ',', aColumnList);	        		
	        	break;
		        }
		        
				if (cUserInfo.getKeyCount()<2) cUserInfo.clear();	        					
        	}
			else
				cUserInfo.clear();
			
			if (!sAuthFile.isEmpty()) {
				frame.readInputListGeneric(cAuthorizations, sMapFile, ',');			
			}
			
			switch (sType) {
			case "github.com":
			default:
				sLocation = "github.com";
				sAccessToken = sGitHubComAccessToken;
				sAPI = "api.github.com";
				break;
			case "ghe":
				sLocation = "github-isl-01.ca.com";
				sAccessToken = sGitHubAccessToken;
				sAPI = "github-isl-01.ca.com/api/v3";
			break;
			}
			
			do {
				JCaContainer cEvents = new JCaContainer();
				readUnprocessedEvents(cEvents, sImagDBPassword, sType);	
				
				for (int iIndex=0; iIndex<cEvents.getKeyElementCount("ApplicationLocation"); iIndex++) {
					String aLocation = cEvents.getString("ApplicationLocation", iIndex);
					if (!sLocation.equalsIgnoreCase(aLocation)) continue;
					
					String sGithubID = cEvents.getString("User_ID", iIndex);
					String sOrg =  cEvents.getString("ResourceOwner1", iIndex);
					String tOrg =  cEvents.getString("ResourceOwner2", iIndex);
					String sRepo = cEvents.getString("ResourceName", iIndex);
					
					if (sType.equalsIgnoreCase("github.com")) {
						int [] iOrg = cOrg.find("organization", sOrg);
						if (iOrg.length == 0) {
							String sProblems = "";
							List<String> ticketProblems = new ArrayList<String>();
							frame.readGHCOrganizationSCIMLinkage(cUserInfo, sOrg, sAccessToken, "C:\\GitHub", sProblems, ticketProblems);
						}
					}
					String sCorpID = lookupCorporateID(sGithubID, cUserInfo, sType);
					
					//2. parse the event type
					String sAttributes = cEvents.getString("EventAttributes", iIndex);
					String eType = "none";
					String rType = "private";
					String uType = "user";
					String sResourceFile = "";
					
					int beginIndex, endIndex;
					String eValue, eName;
					
					while (!sAttributes.isEmpty()) {
						beginIndex = sAttributes.indexOf(':');
						endIndex = sAttributes.indexOf(';');
						if (beginIndex >=0) {
							eName = sAttributes.substring(0, beginIndex);
							if (endIndex >=0) {
								eValue=sAttributes.substring(beginIndex+1, endIndex);
							    sAttributes = sAttributes.substring(endIndex+1);
							}
							else {
								eValue=sAttributes.substring(beginIndex+1);
								sAttributes = "";
							}    
							
							switch (eName) {
							case "eventtype":
								eType = eValue;
								break;
							case "repotype":
								rType = eValue;
								break;
							case "usertype":
								uType = eValue;
							default:
								break;
							}
						}
						else 
							sAttributes = "";
					}
										
					//3. Lookup the user's mail address
					String uMail = "";
					boolean bHasCorporateID = false;
					int[] iUser = cLDAP.find("sAMAccountName", sCorpID);
					if (iUser.length > 0) {
						uMail = cLDAP.getString("mail", iUser[0]);
						bHasCorporateID = true;
					}
					else {
						if (sCorpID.contains("@")) {
							uMail = sCorpID;
						}
					}
					
					String eMail = "SourceCode@ca.com";
					if (!uMail.isEmpty())
						eMail += ";"+uMail;
					
					//If the email is blank, the notice needs to go to the org contacts
					if (eMail.isEmpty()) {
						switch (sOrg) {
						case "RallySoftware":
						case "RallyApps":
						case "RallyTools":
						case "RallyCommunity":
						case "CASaaSOps":
						case "waffleio":
						case "flowdock":
						case "Blazemeter":
						case "CATechnologies":
							eMail = "Team-GIS-githubcom-"+sOrg+"-Contacts@ca.com";
							break;
						default:
							break;
						}
					}
					
					//4. Process Publicized Repos
					if (eType.equalsIgnoreCase("public") || 
						(eType.equalsIgnoreCase("repository") && 
						 rType.equalsIgnoreCase("public")) ) {
						sResourceFile = "Notification_of_Publicized_Repository.txt";
						if (processPolicyOnPublicizedRepositories(sOrg, sRepo)) {
							String sSubject = "A "+sLocation+" Repository Was Made Public Without Authorization";
					        String bodyText = frame.readTextResource(sResourceFile, sOrg, sRepo, sGithubID, sCorpID);								        								          
					        frame.sendEmailNotification(eMail, sSubject, bodyText, true);																				
						}
					}
					else if (eType.equalsIgnoreCase("fork") &&
							 (uType.equalsIgnoreCase("user") || 
							  (uType.equalsIgnoreCase("organization") && !isOrgTracked(tOrg)) ) &&
							 rType.equalsIgnoreCase("private") &&
							 true /* !bHasCorporateID */ ) {
						sResourceFile = "Notification_of_Forked_Repository_by_User.txt";
						
						if (processPolicyOnForkedRepositoriesByUser(sOrg, sRepo, sCorpID)) {							
							String sSubject = "A "+sLocation+" Repository Was Forked Out Of Policy";
					        String bodyText = frame.readTextResource(sResourceFile, sOrg, sRepo, sGithubID, uMail);	
					        // additional substitutions
					        int nIndex;
					        nIndex = bodyText.indexOf("%5");
					        if (nIndex >= 0)
					        	bodyText = bodyText.substring(0, nIndex) +uType+ bodyText.substring(nIndex+2);
					        nIndex = bodyText.indexOf("%6");
					        if (nIndex >= 0)
					        	bodyText = bodyText.substring(0, nIndex) +tOrg+ bodyText.substring(nIndex+2);
					        frame.sendEmailNotification(eMail, sSubject, bodyText, true);																				
						}
					}
				}
				
				Thread.sleep(60000);				
			}
			while (true);
			
		} catch (Exception e) {
			iReturnCode = 1;
		    frame.printErr(e.getLocalizedMessage());			
		    frame.printErr(e.getStackTrace().toString());			
		}
	    System.exit(iReturnCode);		    
		
	} // main
}
