package commonldap.runtime_jar.githubeventldap;

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
	static String sProblems = "";
	static List<String> ticketProblems = new ArrayList<String>();
	
	// Notification
	static String tagUL = "<ul> ";

	GithubEventLdap()
	{
		// Leaving empty
	}
	// main routine
	public static void main(String[] args)
	{
		int iParms = args.length;
		int iReturnCode = 0;
		boolean bProcessChanges = true;
		boolean bLoadOnly = false;
		
		String sInputFile = "";
		String sOutputFile = "";
		String sMapFile = "";
		String sBCC = "";
		String sLogPath = "githubeventldap.log";
				
		String sGitHubAccessToken = "";
		String sGitHubComAccessToken = "";
		String sImagDBPassword = "";
		String sApp, sAppLocation;
		
        
		// check parameters
		for (int i = 0; i < iParms; i++)
		{					
			if (args[i].compareToIgnoreCase("-inputfile") == 0 )
			{
				sInputFile = args[++i];
			}			
			else if (args[i].compareToIgnoreCase("-outputfile") == 0 )
			{
				sOutputFile = args[++i];
			}			
			else if (args[i].compareToIgnoreCase("-mapfile") == 0 )
			{
				sMapFile = args[++i];
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
				frame.printLog("Usage: githubeventldap [-bcc emailaddress] [-log textfile] [-h |-?]");
				frame.printLog(" -bcc option specifies an email address to bcc on notifications sent to users");
				frame.printLog(" -log option specifies location log file.");
				System.exit(iReturnCode);
			}
		} // end for
		
	// 1. Create Frame (e.g. read LDAP users into container)
		JCaContainer cLDAP = new JCaContainer();
		frame = new CommonLdap("IMAGRejectLDAP",
        		               sLogPath,
        		               sBCC,
        		               cLDAP);
	} // main
}
