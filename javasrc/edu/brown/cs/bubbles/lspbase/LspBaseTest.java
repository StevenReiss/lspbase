/********************************************************************************/
/*                                                                              */
/*              LspBaseTest.java                                                */
/*                                                                              */
/*      Test Driver for LSP Base                                                */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2011 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.                            *
 *                                                                               *
 *                        All Rights Reserved                                    *
 *                                                                               *
 * This program and the accompanying materials are made available under the      *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at                                                           *
 *      http://www.eclipse.org/legal/epl-v10.html                                *
 *                                                                               *
 ********************************************************************************/



package edu.brown.cs.bubbles.lspbase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
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
      Thread.sleep(10000);
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
private int		edit_id;
private Element 	last_runevent;
private String          last_endnames;



/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

private LspBaseTest(String [] args)
{
   mint_control = MintControl.create("LSPBASETEST",MintSyncMode.ONLY_REPLIES);
   mint_control.register("<LSPBASE TYPE='_VAR_0' />",new MessageHandler());
   instance_id = "LSPBASE_id";
   edit_id = 1;
   last_runevent = null;
   last_endnames = null;
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
   
   String proj = "alds";
   String ws = "/Users/spr/Lsp/test";
   String fil = "/pro/iot/flutter/alds/lib/main.dart";
   
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
         new CommandArgs("PATTERN","main.dart;initialize()","DEFS",true,"REFS",false,
               "FOR","METHOD"),null);
   sendCommand("EDITPARAM",null,
         new CommandArgs("NAME","AUTOELIDE","VALUE",true),null);
   sendCommand("EDITPARAM",null,
         new CommandArgs("NAME","ELIDEDELAY","VALUE",250),null);    
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




@SuppressWarnings("unused")
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
/*                                                                              */
/*      File methods                                                            */
/*                                                                              */
/********************************************************************************/

private void setupFiles()
{
   String home = System.getProperty("user.home");
   
   try {
      IvyFile.remove(home + "/Lsp/test/alds");
      IvyFile.remove(home + "/Lsp/test/Track");
      IvyFile.remove(home + "/Lsp/test/.projects");
    }
   catch (IOException e) {
      System.err.println("Problme removing old files: " + e);
      System.exit(1);
    }
}


private void setupProject()
{
   String home = System.getProperty("user.home");
   String pfile = home + "/Lsp/test/.projects";
   String pdir = home + "/Lsp/test/alds";
   File pdirf = new File(pdir);
   pdirf.mkdirs();
   File ppfile = new File(pdirf,PROJECT_DATA_FILE);
   
   String pdef = "<PROJECTS>\n" +
         "<PROJECT NAME='alds' PATH='/Users/spr/Lsp/test/alds' />\n" +
         "</PROJECTS>\n";
   
   String tdef = "<PROJECT LANGUAGE='dart' NAME='alds' BASE='/Users/spr/Lsp/test/alds'>\n" +
               "<PATH SOURCE='/pro/iot/flutter/alds' TYPE='INCLUDE' NEST='true' />\n" +
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
      for (int i = 0; i < 500; ++i) {
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
         LspBaseMain.main(new String [] { "-m", "LSPBASETEST", "-ws", "/Users/spr/Lsp/test",
               "-log", "/pro/lspbase/lspbase/src/test.log"});
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





}       // end of class LspBaseTest




/* end of LspBaseTest.java */

