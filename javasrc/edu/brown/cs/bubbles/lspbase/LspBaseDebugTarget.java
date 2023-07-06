/********************************************************************************/
/*                                                                              */
/*              LspBaseDebugTarget.java                                         */
/*                                                                              */
/*      Interface to a running process to debug                                 */
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseDebugTarget implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private LspBaseDebugManager     debug_manager;
private LspBaseLaunchConfig     launch_config;
private LspBaseDebugProtocol    debug_protocol;
private JSONObject              launch_error;
private boolean                 is_running;

private ProcessData             process_data;
private Map<Integer,LspBaseDebugThread> thread_data;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseDebugTarget(LspBaseDebugManager mgr,LspBaseLaunchConfig config)
{
   debug_manager = mgr;
   launch_config = config;
   process_data = null;
   thread_data = new HashMap<>();
   
   debug_protocol = debug_manager.getDebugProtocol(this);
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

LspBaseDebugThread findThreadById(int id)
{
   LspBaseDebugThread th = thread_data.get(id);
   if (th == null) {
      th = new LspBaseDebugThread(this,id);
      thread_data.put(id,th);
    }
   return th;
}

int getId()    
{
   if (process_data == null) return 0;
   
   return process_data.getId();
}


LspBaseBreakpoint findBreakpoint(int id)
{
   return debug_manager.findBreakpoint(id);
}


LspBaseLanguageData getLanguageData()
{
   File f = launch_config.getFileToRun();
   LspBaseProject proj = launch_config.getProject();
   LspBaseFile file = null;
   if (proj != null && f != null) {
      file = proj.findFile(f);
    }
   else if (f != null) {
      LspBaseProjectManager pm = LspBaseMain.getLspMain().getProjectManager();
      file = pm.findFile(null,f.getPath());
    }
   
   LspBaseLanguageData ld = null;
   if (file != null) ld = file.getLanguageData();
   if (ld == null && proj != null) ld = proj.getLanguageData();
   return ld;
}


/********************************************************************************/
/*                                                                              */
/*      Action methods                                                          */
/*                                                                              */
/********************************************************************************/

void evaluateExpression(String bid,String eid,String expr,int frame,boolean stop)
{ }

void startDebug() throws LspBaseException    
{
   JSONObject dbgcfg = debug_protocol.getLanguage().getDebugConfiguration();
    
   String attach = launch_config.getConnectMap();
   launch_error = null;
   is_running = false;
   
   if (attach == null) {
      JSONObject args = dbgcfg.getJSONObject("launchData");
      args = fixDebugInfo(args);
      debug_protocol.sendJsonRequest("launch",this::debugStarted,args);
    }
   else {
      JSONObject args = dbgcfg.getJSONObject("attachData");
      args = fixDebugInfo(args);
      debug_protocol.sendJsonRequest("attach",this::debugStarted,args);
    }
   
   if (launch_error != null) {
      throw new LspBaseException("Start debug failed: " + launch_error.optString("error"));
    }
   else {
      is_running = true;
    }
}


private JSONObject fixDebugInfo(JSONObject data)
{
   if (data == null) return null;
   
   JSONObject rslt = new JSONObject();
   for (Iterator<String> it = data.keys(); it.hasNext(); ) {
      String key = it.next();
      Object v = data.get(key);
      if (v instanceof String && v.toString().startsWith("$")) {
         String s = (String) v;
         Object sub = null;
         switch (s) {
            case "$WD" :
               sub = launch_config.getWorkingDirectory().getPath();
               break;
            case "$ENV" :
               sub = new JSONArray();
               break;
            case "$START" :
               sub = launch_config.getFileToRun();
               break;
            case "$ARGS" :
               sub = launch_config.getProgramArguments();
               break;
            case "$VMARGS" :
               sub = launch_config.getVMArguments();
               break;
            case "$TOOLARGS" :
               sub = launch_config.getToolArguments();
               break;
            default :
               break;
          }
         if (sub != null) rslt.put(key,sub);
       }
      else {
         rslt.put(key,data.get(key));
       }
    }
   
   return rslt;
}



private void debugStarted(Object data,JSONObject err)
{ 
   launch_error = err;
}


boolean debugAction(LspBaseDebugAction action)  
{
   if (!is_running) return false;
   
   switch (action) {
      case NONE :
         break;
      case RESUME :
         break;
      case STEP_INTO :
         break;
      case STEP_OVER : 
         break;
      case STEP_RETURN :
         break;
      case DROP_TO_FRAME :
         break;
      case SUSPEND :
         break;
      case TERMINATE :
         break;
    }
   
   return false; 
}


/********************************************************************************/
/*                                                                              */
/*      Handle run time events                                                  */
/*                                                                              */
/********************************************************************************/

void processEvent(String event,JSONObject body)
{
   switch (event) {
      case "process" :
         process_data = new ProcessData(body);
         break;
      case "thread" :
         int id = body.getInt("threadId");
         String reason = body.getString("reason");
         LspBaseDebugThread thrd = findThreadById(id);
         thrd.setState(reason);
         if (reason.equals("exited")) {
            thread_data.remove(thrd.getId());
            postThreadEvent(thrd,"TERMINATE",null,null,false);
          }
         else if (reason.equals("started")) {
            postThreadEvent(thrd,"CREATE",null,null,false);
          }
         else {
            postThreadEvent(thrd,"CHANGE",null,null,false);
          }
         break;
      case "stopped" :
         if (body.optBoolean("allThreadsStopped")) {
            for (LspBaseDebugThread dt : thread_data.values()) {
               dt.handleStopped(body);
             }
          }
         else {
            LspBaseDebugThread dt = findThreadById(body.getInt("threadId"));
            dt.handleStopped(body);
          }
         break;
      case "continued" :
         if (body.optBoolean("allThreadsContinued")) {
            for (LspBaseDebugThread dt : thread_data.values()) {
               dt.handleContinued(body);
             }
          }
         else {
            LspBaseDebugThread dt = findThreadById(body.getInt("threadId"));
            dt.handleContinued(body);
          }
         break;
      case "terminated" :
         for (LspBaseDebugThread dt : thread_data.values()) {
            dt.handleTerminated();
          }
         thread_data.clear();
         process_data.handleTerminated();
         break;
    }
}



/********************************************************************************/
/*                                                                              */
/*      Send updates to Bubbles                                                 */
/*                                                                              */
/********************************************************************************/

void postThreadEvent(LspBaseDebugThread thrd,String kind,String detail,String data,boolean iseval)
{
   LspBaseMain lsp = LspBaseMain.getLspMain();
   IvyXmlWriter xw = lsp.beginMessage("RUNEVENT");
   xw.field("TIME",System.currentTimeMillis());
   outputThreadEvent(xw,thrd,kind,detail,data,iseval);
   lsp.finishMessageWait(xw);
}


private void outputThreadEvent(IvyXmlWriter xw,LspBaseDebugThread thrd,
      String kind,String detail,String data,boolean iseval)
{
   xw.begin("RUNEVENT");
   xw.field("KIND",kind);
   if (detail != null) xw.field("DETAIL",detail);
   if (data != null) xw.field("DATA",data);
   xw.field("EVAL",iseval);
   xw.field("TYPE","THREAD");
   thrd.outputXml(xw);
   xw.end("RUNEVENT");
}



/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

void outputLaunch(IvyXmlWriter xw)
{
   xw.begin("LAUNCH");
   xw.field("ID",getId());
   xw.field("CID",launch_config.getId());
   xw.field("MODE","debug");
   xw.end("LAUNCH");
}


/********************************************************************************/
/*                                                                              */
/*      Process data                                                            */
/*                                                                              */
/********************************************************************************/

private class ProcessData {

   private String process_name;
   private int system_id;
   private boolean is_local;
   private String launch_type;
   private int pointer_size;
         
   ProcessData(JSONObject body) {
      process_name = body.getString("name");
      system_id = body.optInt("systemProcessId");
      is_local = body.optBoolean("isLocalProcess",true);
      launch_type = body.optString("startMethod","launch");
      pointer_size = body.optInt("pointerSize");
    }
   
   void handleTerminated()              { }
   
   String getName()                     { return process_name; }
   
   int getId() {
      if (system_id > 0) return system_id;
      return hashCode(); 
    }
      
}       // end of inner class ProcessData



}       // end of class LspBaseDebugTarget




/* end of LspBaseDebugTarget.java */

