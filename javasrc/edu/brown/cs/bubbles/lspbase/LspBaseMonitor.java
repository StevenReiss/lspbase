/********************************************************************************/
/*                                                                              */
/*              LspBaseMonitor.java                                             */
/*                                                                              */
/*      Handle interactions with the message server                             */
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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TimerTask;

import org.w3c.dom.Element;

import edu.brown.cs.ivy.mint.MintArguments;
import edu.brown.cs.ivy.mint.MintConstants;
import edu.brown.cs.ivy.mint.MintControl;
import edu.brown.cs.ivy.mint.MintDefaultReply;
import edu.brown.cs.ivy.mint.MintHandler;
import edu.brown.cs.ivy.mint.MintMessage;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseMonitor implements LspBaseConstants, MintConstants  
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private LspBaseMain             lsp_base;
private String			mint_handle;
private MintControl		mint_control;
private boolean 		shutdown_mint;
private int			num_clients;
private Object                  send_sema;
private Pinger                  lspbase_pinger;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseMonitor(LspBaseMain bm,String mint) throws LspBaseException
{
   lsp_base = bm;
   mint_handle = mint;
   
   send_sema = new Object();
   
   mint_control = MintControl.create(mint_handle,MintSyncMode.SINGLE);
   mint_control.register("<BUBBLES DO='_VAR_1' />",new CommandHandler());
}



/********************************************************************************/
/*                                                                              */
/*      Shutdown methods                                                        */
/*                                                                              */
/********************************************************************************/

synchronized void waitForShutDown()
{
   while (!shutdown_mint) {
      try {
         wait(5000);
       }
      catch (InterruptedException e) { }
    }
}



/********************************************************************************/
/*										*/
/*	Message Routines							*/
/*										*/
/********************************************************************************/

public IvyXmlWriter beginMessage(String typ)
{
   return beginMessage(typ,null);
}


public IvyXmlWriter beginMessage(String typ,String bid)
{
   IvyXmlWriter xw = new IvyXmlWriter();
   xw.begin("LSPBASE");
   xw.field("SOURCE","LSPBASE");
   xw.field("TYPE",typ);
   if (bid != null) xw.field("BID",bid);
   
   return xw;
}


public void finishMessage(IvyXmlWriter xw)
{
   xw.end("LSPBASE");
   
   sendMessage(xw.toString());
}


public String finishMessageWait(IvyXmlWriter xw)
{
   return finishMessageWait(xw,0);
}


String finishMessageWait(IvyXmlWriter xw,long delay)
{
   xw.end("LSPBASE");
   
   return sendMessageWait(xw.toString(),delay);
}



private void sendMessage(String msg)
{
   synchronized (send_sema) {
      LspLog.logD("Sending: " + msg);
      if (mint_control != null && !shutdown_mint)
	 mint_control.send(msg);
    }
}



private String sendMessageWait(String msg,long delay)
{
   MintDefaultReply rply = new MintDefaultReply();
   
   synchronized (send_sema) {
      LspLog.logD("Sending/w: " + msg);
      if (mint_control != null && !shutdown_mint) {
          mint_control.send(msg,rply,MINT_MSG_FIRST_NON_NULL);
       }
      else return null;
    }
   
   return rply.waitForString(delay);
}




/********************************************************************************/
/*										*/
/*	Command processing							*/
/*										*/
/********************************************************************************/

private String handleCommand(String cmd,String proj,Element xml) throws LspBaseException
{
   LspLog.logI("Handle command " + cmd + " for " + proj);
   LspLog.logD("Full command " + IvyXml.convertXmlToString(xml));
   
   IvyXmlWriter xw = new IvyXmlWriter();
   xw.begin("RESULT");
   
   switch (cmd) {
      case "PING" :
	 xw.text("PONG");
	 break;
      case "ENTER" :
	 if (num_clients == 0 && lspbase_pinger == null) {
	    lspbase_pinger = new Pinger();
	    lsp_base.scheduleTask(lspbase_pinger,10000,10000);
	  }
	 ++num_clients;
	 xw.text(Integer.toString(num_clients));
	 break;
      case "EXIT" :
	 if (--num_clients <= 0) {
	    LspLog.logD("Stopping application");
	    shutdown_mint = true;    
          }
         break;
      case "LOGLEVEL" :
	 LspBaseLogLevel lvl = IvyXml.getAttrEnum(xml,"LEVEL",LspBaseLogLevel.ERROR);
         LspLog.setLogLevel(lvl);
	 break;
      case "GETHOST" :
	 handleGetHost(xw);
	 break;
      case "LANGUAGEDATA" :
	 getLanguageData(proj,xw);
	 break;
         
      case "PROJECTS" :
      case "OPENPROJECT" :
      case "BUILDPROJECT" :
      case "CREATEPROJECT" :
      case "EDITPROJECT" :
      case "CREATEPACKAGE" :
      case "FINDPACKAGE" :
      case "CREATECLASS" :
      case "PATTERNSEARCH" :
      case "SEARCH" :
      case "FINDBYKEY" :
      case "FINDDEFINITIONS" :
      case "FINDREFERENCES" :
      case "GETFULLYQUALIFIEDNAME" :
      case "FINDREGIONS" :
      case "GETALLNAMES" :
      case "PREFERENCES" :
      case "SETPREFERENCES" :
         lsp_base.getProjectManager().handleCommand(cmd,proj,xml,xw);
         break;
      case "EDITPARAM" :
      case "ELIDESET" :
      case "STARTFILE" :
      case "COMMIT" :
      case "EDITFILE" :
      case "INDENT" :
      case "FIXINDENTS" :
      case "GETCOMPLETIONS" :
      case "CREATEPRIVATE" :
      case "PRIVATEEDIT" :
      case "REMOVEPRIVATE" :
      case "QUICKFIX" :
      case "FIXIMPORTS" :
      case "RENAME" :
      case "FORMATCODE" :
      case "DELETE" :
      case "RENAMERESOURCE" :
      case "HOVERDATA" :
         lsp_base.getProjectManager().handleEditCommand(cmd,proj,xml,xw);
         break;
      case "LAUNCHQUERY" :
      case "GETRUNCONFIG" :
      case "NEWRUNCONFIG" :
      case "EDITRUNCONFIG" :
      case "SAVERUNCONFIG" :
      case "DELETERUNCONFIG" :
      case "GETALLBREAKPOINTS" :
      case "ADDLINEBREAKPOINT" :
      case "ADDEXCEPTIONBREAKPOINT" :
      case "EDITBREAKPOINT" :
      case "CLEARALLLINEBREAKPOINTS" :
      case "CLEARLINEBREAKPOINT" :
      case "START" :
      case "DEBUGACTION" :
      case "CONSOLEINPUT" :
      case "GETSTACKFRAMES" :
      case "VARVAL" :
      case "VARDETAIL" :
      case "EVALUATE" :
         lsp_base.getDebugManager().handleCommand(cmd,proj,xml,xw);
         break;
         
      case "SAVEWORKSPACE" :
         xw.text("SAVED");
         break;
         
      default :
	 xw.close();
	 throw new LspBaseException("Unknown LSPBASE command " + cmd);
    }
   
   xw.end("RESULT");
   
   LspLog.logD("Result = " + xw.toString());
   String rslt = xw.toString();
   
   xw.close();
   return rslt;
}


/********************************************************************************/
/*                                                                              */
/*      Command processing methods                                              */
/*                                                                              */
/********************************************************************************/

static Set<String> getSet(Element xml,String key)
{
   Set<String> items = null;
   
   for (Element c : IvyXml.children(xml,key)) {
      String v = IvyXml.getText(c);
      if (v == null || v.length() == 0) continue;
      if (items == null) items = new LinkedHashSet<String>();
      items.add(v);
    }
   
   return items;
}


static List<LspBaseEdit> getEditSet(Element xml)
{
   List<LspBaseEdit> edits = new ArrayList<>();
   
   for (Element c : IvyXml.children(xml,"EDIT")) {
      LspBaseEdit edi = new LspBaseEdit(c);
      edits.add(edi);
    }
   
   return edits;
}





static List<Element> getElements(Element xml,String key)
{
   List<Element> elts = null;
   
   for (Element c : IvyXml.children(xml,key)) {      
      if (elts == null) elts = new ArrayList<Element>();
      elts.add(c);
    }
   
   return elts;
}



/********************************************************************************/
/*                                                                              */
/*      Utility methods                                                         */
/*                                                                              */
/********************************************************************************/

private void handleGetHost(IvyXmlWriter xw)
{
   String h1 = null;
   String h2 = null;
   String h3 = null;
   try {
      InetAddress lh = InetAddress.getLocalHost();
      h1 = lh.getHostAddress();
      h2 = lh.getHostName();
      h3 = lh.getCanonicalHostName();
    }
   catch (IOException e) { }
   if (h1 != null) xw.field("ADDR",h1);
   if (h2 != null) xw.field("NAME",h2);
   if (h3 != null) xw.field("CNAME",h3);
}


/********************************************************************************/
/*										*/
/*	Configuration Support			        			*/
/*										*/
/********************************************************************************/

private void getLanguageData(String proj,IvyXmlWriter xw)
{
   String lang = null;
   
   if (proj != null) {
      try {
	 LspBaseProject lspproj = lsp_base.getProjectManager().findProject(proj);
	 lang = lspproj.getLanguageData().getName();
       }
      catch (LspBaseException e) {
	 lang = lsp_base.getBaseLanguage();
       }
    }
   else {
      lang = lsp_base.getBaseLanguage();
    }
   
   String nm = "lspbase-launches-" + lang + ".xml";
   InputStream ins = LspBaseMain.getResourceAsStream(nm);
   Element xml = IvyXml.loadXmlFromStream(ins);
   xw.writeXml(xml);
}




/********************************************************************************/
/*										*/
/*	Command handler for MINT						*/
/*										*/
/********************************************************************************/

private class CommandHandler implements MintHandler {
   
   @Override public void receive(MintMessage msg, MintArguments args) {
      String cmd = args.getArgument(1);
      Element xml = msg.getXml();
      String proj = IvyXml.getAttrString(xml,"PROJECT");
      
      String rslt = null;
      
      try {
         rslt = handleCommand(cmd,proj,xml);
       }
      catch (LspBaseException e) {
         String xmsg = "Error in command " + cmd + ": " + e;
         LspLog.logE(xmsg,e);
         rslt = "<ERROR><![CDATA[LSPBASE: " + xmsg + "]]></ERROR>";
       }
      catch (Throwable t) {
         String xmsg = "Problem processing command " + cmd + ": " + t;
         LspLog.logE(xmsg,t);
         StringWriter sw = new StringWriter();
         PrintWriter pw = new PrintWriter(sw);
         t.printStackTrace(pw);
         rslt = "<ERROR>";
         rslt += "<MESSAGE>LSPBASE: " + xmsg + "</MESSAGE>";
         rslt += "<EXCEPTION><![CDATA[" + t.toString() + "]]></EXCEPTION>";
         rslt += "<STACK><![CDATA[" + sw.toString() + "]]></STACK>";
         rslt += "</ERROR>";
       }
      
      msg.replyTo(rslt);
      
      if (shutdown_mint) {
         mint_control.shutDown();
         synchronized (LspBaseMonitor.this) {
            LspBaseMonitor.this.notifyAll();
          }
       }
    }
   
}	// end of subclass CommandHandler




/********************************************************************************/
/*										*/
/*	Pinging task to determine if front end crashed				*/
/*										*/
/********************************************************************************/

private class Pinger extends TimerTask {

   private int error_count;
   
   Pinger() {
      error_count = 0;
    }
   
   @Override public void run() {
      if (shutdown_mint) return;
      if (num_clients == 0) return;
      IvyXmlWriter xw = beginMessage("PING");
      String rslt = finishMessageWait(xw,1000);
      if (rslt == null) {
         ++error_count;
         if (error_count > 3) {
            num_clients = 0;
            shutdown_mint = true;
            mint_control.shutDown();
            synchronized (LspBaseMonitor.this) {
               LspBaseMonitor.this.notifyAll();
             }
          }
       }
   else error_count = 0;
}

}




}       // end of class LspBaseMonitor




/* end of LspBaseMonitor.java */

