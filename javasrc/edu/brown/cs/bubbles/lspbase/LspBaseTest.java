/********************************************************************************/
/*										*/
/*		LspBaseTest.java						*/
/*										*/
/*	Test Driver for LSP Base						*/
/*										*/
/********************************************************************************/
/*	Copyright 2011 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 * This program and the accompanying materials are made available under the	 *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at								 *
 *	http://www.eclipse.org/legal/epl-v10.html				 *
 *										 *
 ********************************************************************************/



package edu.brown.cs.bubbles.lspbase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;

import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.mint.MintArguments;
import edu.brown.cs.ivy.mint.MintConstants;
import edu.brown.cs.ivy.mint.MintControl;
import edu.brown.cs.ivy.mint.MintDefaultReply;
import edu.brown.cs.ivy.mint.MintHandler;
import edu.brown.cs.ivy.mint.MintMessage;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

public class LspBaseTest implements LspBaseConstants, MintConstants
{


/********************************************************************************/
/*										*/
/*	Main Program								*/
/*										*/
/********************************************************************************/

public static void main(String [] args)
{
   LspBaseTest nt = new LspBaseTest(args);

   nt.runTest();

   try {
      Thread.sleep(3000);
    }
   catch (InterruptedException e) { }

   nt.sendCommand("EXIT",null,null,null);
}


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private MintControl	mint_control;
private String		instance_id;
private Element 	last_runevent;
private String		last_endnames;
private String          language_name;
private LanguageData    language_data;

private static Map<String,LanguageData> language_map;

static {
   language_map = new HashMap<>();
   language_map.put("dart",
         new LanguageData("alds",
               " Lsp/test",
               "/pro/iot/flutter/alds/lib/main.dart",
               "main.dart;initialize()"));
   language_map.put("ts",
         new LanguageData("iqsign",
               "Lsp/test1",
               "/pro/iot/iqsign/server.js",
               "server.js;errorHandler()"));
}



/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

private LspBaseTest(String [] args)
{
   language_name = null;
   scanArgs(args);
  
   mint_control = MintControl.create("LSPBASETEST",MintSyncMode.ONLY_REPLIES);
   mint_control.register("<LSPBASE TYPE='_VAR_0' />",new MessageHandler());
   instance_id = "LSPBASE_id";
   last_runevent = null;
   last_endnames = null;
}



/********************************************************************************/
/*                                                                              */
/*      Handle options                                                          */
/*                                                                              */
/********************************************************************************/

private void scanArgs(String [] args)
{
   for (int i = 0; i < args.length; ++i) {
      if (args[i].startsWith("-")) {
         if (args[i].startsWith("-lang") && i+1 < args.length) {
            language_name = args[++i];
          }
         else badArgs();
       }
      else {
         if (language_name == null) language_name = args[i];
         else badArgs();
       }
    }
   if (language_name == null) language_name = "dart";
   language_data = language_map.get(language_name);
   if (language_data == null) badArgs();
}


private void badArgs()
{
   System.err.println("LspBaseTest [-lang <dart|ts>]");
   System.exit(1);
}


/********************************************************************************/
/*										*/
/*	Actual test code							*/
/*										*/
/********************************************************************************/

private void runTest()
{
   setupFiles();
   setupProject();

   start();

   String proj = language_data.getProject();
   String ws = language_data.getWorkspace();
   String fil = language_data.getFile();
   int editid = 1;

   sendCommand("PING",null,null,null);
   sendCommand("ENTER",null,null,null);
   sendCommand("LOGLEVEL",null,new CommandArgs("LEVEL","DEBUG"),null);
   sendCommand("GETHOST",null,null,null);
   sendCommand("PREFERENCES",null,null,null);
   sendCommand("PROJECTS",null,null,null);
   sendCommand("PROJECTS",null,new CommandArgs("WS",ws),null);
   sendCommand("BUILDPROJECT",proj,
	 new CommandArgs("REFRESH",false,"CLEAN",false,"FULL",false,"WS",ws),null);
   sendCommand("GETALLBREAKPOINTS",null,null,null);
   sendCommand("GETRUNCONFIG",null,null,null);
   sendCommand("GETALLNAMES",null,
	 new CommandArgs("BACKGROUND","NAME_1234"),null);
   waitForNames();
   sendCommand("PROJECTS",null,new CommandArgs("WS",ws),null);
   sendCommand("OPENPROJECT",proj,
	 new CommandArgs("PATHS",true),null);
   sendCommand("PATTERNSEARCH",proj,
	 new CommandArgs("PATTERN",language_data.getSearchFor(),"DEFS",true,"REFS",false,
	       "FOR","METHOD"),null);
   sendCommand("EDITPARAM",null,
	 new CommandArgs("NAME","AUTOELIDE","VALUE",true),null);
   sendCommand("EDITPARAM",null,
	 new CommandArgs("NAME","ELIDEDELAY","VALUE",1000),null);
   sendCommand("ELIDESET",proj,
	 new CommandArgs("FILE",fil,"COMPUTE",true),
	 "<REGION START='1993' END='2392' />");
   sendCommand("ELIDESET",proj,
	 new CommandArgs("FILE",fil,"COMPUTE",true),null);
   sendCommand("STARTFILE",proj,
	 new CommandArgs("FILE",fil,"ID",2),null);
   lookupPoint(proj,fil,2154);
   lookupPoint(proj,fil,2114);
   lookupPoint(proj,fil,2120);
   sendCommand("COMMIT",proj,
	 new CommandArgs("SAVE",true),null);
   sendCommand("COMMIT",proj,
	 new CommandArgs("COMPILE",true),null);
   
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2106),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2165),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2145),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",1990),null);

   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2563),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2611),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2644),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2659),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2695),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2736),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2741),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2775),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2816),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2866),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2904),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2922),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2936),null);
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2945),null);
// sendCommand("INDENT",proj,
// 	 new CommandArgs("FILE",fil,"ID",editid++,"OFFSET",2217,"SPLIT",true),null);
   sendCommand("FINDREGIONS",proj,
         new CommandArgs("FILE",fil,"ALL",true),null);
   sendCommand("FINDREGIONS",proj,
         new CommandArgs("FILE",fil,"FIELDS",true),null);
   sendCommand("FINDREGIONS",proj,
         new CommandArgs("FILE",fil,"COMPUNIT",true),null);
   sendCommand("FINDREGIONS",proj,
         new CommandArgs("FILE",fil,"PREFIX",true),null);
   sendCommand("FINDREGIONS",proj,
         new CommandArgs("FILE",fil,"STATICS",true),null);
   sendCommand("FINDREGIONS",proj,
         new CommandArgs("FILE",fil,"IMPORTS",true,"PACKAGE",true,"TOPDECLS",true),
         null); 
   
   sendCommand("GETCOMPLETIONS",proj,
         new CommandArgs("FILE",fil,"OFFSET",2026),null);
   sendCommand("CREATEPRIVATE",proj,
         new CommandArgs("FILE",fil,"PID","test12345"),null);
   sendCommand("PRIVATEEDIT",proj,
         new CommandArgs("FILE",fil,"PID","test12345"),
         "<EDIT START='2035' END='2035'><![CDATA[garbage inserted]]></EDIT>");
   sendCommand("REMOVEPRIVATE",proj,
         new CommandArgs("FILE",fil,"PID","test12345"),null);
   
   sendCommand("EDITFILE",proj,
	 new CommandArgs("FILE",fil,"ID",editid++),
	 "<EDIT START='2035' END='2037' />");
   delay(2000);
   
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid,"OFFSET",2041),null);
   sendCommand("EDITFILE",proj,
	 new CommandArgs("FILE",fil,"ID",editid++),
	 "<EDIT START='2035' END='2035'><![CDATA[    ]]></EDIT>");
   delay(2000);
   
   sendCommand("INDENT",proj,
	 new CommandArgs("FILE",fil,"ID",editid,"OFFSET",2041),null);
   sendCommand("FIXINDENTS",proj,
         new CommandArgs("FILE",fil,"ID",editid,"OFFSET",2041),null);
   
   sendCommand("COMMIT",proj,
	 new CommandArgs("REFRESH",true),null);
   
   sendCommand("ELIDESET",proj,
	 new CommandArgs("FILE",fil,"COMPUTE",true),
	 "<REGION START='0' END='2946' />");
   sendCommand("SAVEWORKSPACE",null,
	 new CommandArgs("WS",ws),null);
}

private void lookupPoint(String proj,String fil,int where)
{
   sendCommand("FINDREFERENCES",proj,
	 new CommandArgs("FILE",fil,"START",where,"END",where,"RONLY",true,
	       "EXACT",true,"EQUIV",true),null);
   sendCommand("FINDREFERENCES",proj,
	 new CommandArgs("FILE",fil,"START",where,"END",where,"RONLY",true,
	       "EXACT",true,"EQUIV",true),null);
   sendCommand("FINDREFERENCES",null,
	 new CommandArgs("FILE",fil,"START",where,"END",where,"WONLY",true,
	       "EXACT",true,"EQUIV",true),null);
   sendCommand("FINDDEFINITIONS",null,
	 new CommandArgs("FILE",fil,"START",where,"END",where),null);
   sendCommand("GETFULLYQUALIFIEDNAME",null,
	 new CommandArgs("FILE",fil,"START",where,"END",where),null);

}




private void delay(int time)
{
   try {
      Thread.sleep(time);
    }
   catch (InterruptedException e) { }
}


@SuppressWarnings("unused")
private void waitForRunEvent(long wait)
{
   long now = System.currentTimeMillis();
   synchronized (this) {
      for ( ; ; ) {
	 if (wait > 0) {
	    long delta = System.currentTimeMillis() - now;
	    if (delta > wait) {
	       LspLog.logI("LSPBASETEST: Wait timed out");
	       return;
	     }
	  }
	 if (last_runevent == null) {
	    try {
	       wait(1000);
	     }
	    catch (InterruptedException e) { }
	  }
	 if (last_runevent != null) {
	    String kind = IvyXml.getAttrString(last_runevent,"KIND");
	    last_runevent = null;
	    if (kind != null && kind.equals("SUSPEND")) break;
	  }
       }
    }
}



private void waitForNames()
{
   synchronized (this) {
      while (last_endnames == null) {
	 try {
	    wait(1000);
	  }
	 catch (InterruptedException e) { }
       }
      last_endnames = null;
    }
}



/********************************************************************************/
/*										*/
/*	File methods								*/
/*										*/
/********************************************************************************/

private void setupFiles()
{
   String ws = language_data.getWorkspace();
   try {
      File fws = new File(ws);
      fws.mkdirs();
      IvyFile.remove(ws + "/" + language_data.getProject());
      IvyFile.remove(ws + "Track");
      IvyFile.remove(ws + ".projects");
    }
   catch (IOException e) {
      System.err.println("Problem removing old files: " + e);
      System.exit(1);
    }
}


private void setupProject()
{
   String ws = language_data.getWorkspace();
   String pfile = ws + "/.projects";
   String pdir = ws + "/" + language_data.getProject();
   File pdirf = new File(pdir);
   pdirf.mkdirs();
   File ppfile = new File(pdirf,PROJECT_DATA_FILE);

   String pdef = "<PROJECTS>\n" +
	 "<PROJECT NAME='" + language_data.getProject() + "'" +
         " PATH='" + pdir + "' />\n" +
	 "</PROJECTS>\n";

   String tdef = "<PROJECT LANGUAGE='" + language_name + "'" +
               " NAME='" + language_data.getProject() + "'" +
               " BASE='" + pdir + "'>\n" +
	       "<PATH SOURCE='" + language_data.getSourcePath() + "'" + 
               " TYPE='INCLUDE' NEST='true' />\n" +
	       "<PATH SOURCE='**/bBACKUP' TYPE='EXCLUDE' />\n" +
	       "</PROJECT>";

   try (PrintWriter pw = new PrintWriter(new FileWriter(pfile))) {
      pw.println(pdef);
    }
   catch (IOException e) { }

   try (PrintWriter pw = new PrintWriter(new FileWriter(ppfile))) {
      pw.println(tdef);
    }
   catch (IOException e) { }
}



/********************************************************************************/
/*										*/
/*	Run the server methods							*/
/*										*/
/********************************************************************************/

private void start()
{
   if (!tryPing()) {
      Runner r = new Runner();
      r.start();
      LspLog.logI("LSPBASETEST: STARTING");
      for (int i = 0; i < 5000; ++i) {
	 if (tryPing()) break;
	 try {
	    Thread.sleep(1000);
	  }
	 catch (InterruptedException e) { }
       }
      if (!tryPing()) {
	 LspLog.logE("LSPBASETEST: Starting failed");
       }
    }
}




private class Runner extends Thread {

   Runner() {
      super("LspBaseRunnerThread");
    }

   @Override public void run() {
      try {
         LspLog.logI("LSPBASETEST: Begin");
         LspBaseMain.main(new String [] { "-m", "LSPBASETEST", 
               "-ws", language_data.getWorkspace(),
               "-log", "/pro/lspbase/lspbase/src/test.log",
               "-lang", language_name
          });
         LspLog.logI("LSPBASETEST: Start run");
       }
      catch (Throwable t) {
         LspLog.logE("LSPBASETEST: Error running: " + t);
         t.printStackTrace();
       }
      LspLog.logI("LSPBASETEST: Finish run");
    }

}	// end of inner class Runner




/********************************************************************************/
/*										*/
/*	Messaging methods							*/
/*										*/
/********************************************************************************/

private ReplyHandler sendCommand(String cmd,String proj,CommandArgs args,String cnts)
{
   ReplyHandler rh = new ReplyHandler(cmd);
   String msg = null;

   try (IvyXmlWriter xw = new IvyXmlWriter()) {
      xw.begin("BUBBLES");
      xw.field("DO",cmd);
      xw.field("LANG","LSP");
      xw.field("BID",instance_id);
      if (proj != null) xw.field("PROJECT",proj);
      if (args != null) {
	 for (Map.Entry<String,Object> ent : args.entrySet()) {
	    xw.field(ent.getKey(),ent.getValue());
	  }
       }
      if (cnts != null) xw.xmlText(cnts);
      xw.end("BUBBLES");
      msg = xw.toString();

    }

   LspLog.logI("LSPBASETEST: BEGIN COMMAND " + cmd);
   LspLog.logI("LSPBASETEST: SENDING: " + msg);

   synchronized (this) {
      last_runevent = null;
    }

   mint_control.send(msg,rh,MINT_MSG_FIRST_NON_NULL);

   rh.print();

   return rh;
}


private boolean tryPing()
{
   ReplyHandler rh = sendCommand("PING",null,null,null);
   String s = rh.waitForString();
   return s != null;
}



/********************************************************************************/
/*										*/
/*	Reply handler								*/
/*										*/
/********************************************************************************/

private static class ReplyHandler extends MintDefaultReply {

   private String cmd_name;
   private String result_value;

   ReplyHandler(String what) {
      cmd_name = what;
    }

   void print() {
      String rslt = waitForString();
      result_value = rslt;
      if (rslt == null) {
         LspLog.logI("LSPBASETEST: No reply for " + cmd_name);
       }
      else {
         LspLog.logI("LSPBASETEST: Reply for " + cmd_name + ":");
         LspLog.logI(rslt);
         LspLog.logI("LSPBASETEST: End of reply");
       }
    }

   @SuppressWarnings("unused")
   String getResult() {
      return result_value;
    }

}	// end of inner class ReplyHandler



/********************************************************************************/
/*										*/
/*	Message handler 							*/
/*										*/
/********************************************************************************/

private class MessageHandler implements MintHandler {

   @Override public void receive(MintMessage msg,MintArguments args) {
      LspLog.logI("LSPBASETEST: Message from LSPBASE:");
      LspLog.logI(msg.getText());
      LspLog.logI("LSPBASETEST: End of Message");
      Element xml = msg.getXml();
      LspBaseTest test = LspBaseTest.this;
      synchronized (test) {
	 switch (IvyXml.getAttrString(xml,"TYPE")) {
	    case "RUNEVENT" :
	       Element re = IvyXml.getChild(xml,"RUNEVENT");
	       String kind = IvyXml.getAttrString(re,"KIND");
	       if (kind.equals("RESUME") || kind.equals("SUSPEND")) {
		  test.last_runevent = re;
		  test.notifyAll();
		}
	       break;
	    case "ENDNAMES" :
	       String nid = IvyXml.getAttrString(xml,"NID");
	       test.last_endnames = nid;
	       break;
	  }
       }
      msg.replyTo();
    }

}	// end of inner class MessageHandler




/********************************************************************************/
/*                                                                              */
/*      Language data                                                           */
/*                                                                              */
/********************************************************************************/

private static class LanguageData {
   
   private String project_name;
   private String workspace_name;
   private String file_name;
   private String search_for;
   
   LanguageData(String pnm,String wnm,String fnm,String search) {
      project_name = pnm;
      if (!wnm.startsWith("/")) wnm = System.getProperty("user.home") + "/" + wnm;
      workspace_name = wnm;
      file_name = fnm;
      search_for = search;
    }
   
   String getProject()                          { return project_name; }
   String getWorkspace()                        { return workspace_name; }
   String getFile()                             { return file_name; }
   String getSearchFor()                        { return search_for; }
   
   String getSourcePath() {
      File f1 = new File(file_name);
      File f2 = f1.getParentFile();
      if (f2.getName().equals("lib")) f2 = f2.getParentFile();
      return f2.getPath();
    }
   
}       // end of inner class LanguageData



}	// end of class LspBaseTest




/* end of LspBaseTest.java */

