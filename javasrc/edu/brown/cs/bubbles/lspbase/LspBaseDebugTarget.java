/********************************************************************************/
/*										*/
/*		LspBaseDebugTarget.java 					*/
/*										*/
/*	Interface to a running process to debug 				*/
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.brown.cs.ivy.exec.IvyExec;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseDebugTarget implements LspBaseConstants
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private LspBaseDebugManager	debug_manager;
private LspBaseLaunchConfig	launch_config;
private LspBaseDebugProtocol	debug_protocol;
private JSONObject		launch_error;
private boolean 		is_running;
private boolean                 is_terminated;
private IvyExec 		cur_exec;
private int			launch_pid;
private boolean                 use_flutter;

private ProcessData		process_data;
private Map<Integer,LspBaseDebugThread> thread_data;

private Set<String> local_scopes;
private Set<String> class_scopes;
private Set<String> primitive_types;
private Set<String> string_types;


private static AtomicInteger	pid_counter = new AtomicInteger(1000000);



/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

LspBaseDebugTarget(LspBaseDebugManager mgr,LspBaseLaunchConfig config)
{
   debug_manager = mgr;
   launch_config = config;
   process_data = null;
   thread_data = new HashMap<>();
   cur_exec = null;
   use_flutter = true;          // should get from config
   is_running = false;
   is_terminated = false;
   
   launch_pid = pid_counter.getAndIncrement();
   
   debug_protocol = debug_manager.getDebugProtocol(this);
   LspBaseLanguageData ld = debug_protocol.getLanguage();
   local_scopes = ld.getCapabilitySet("localScopes");
   class_scopes = ld.getCapabilitySet("classScopes");
   primitive_types = ld.getCapabilitySet("primitiveTypes");
   string_types = ld.getCapabilitySet("stringTypes");
}


/********************************************************************************/
/*										*/
/*	Access methods								*/
/*										*/
/********************************************************************************/

LspBaseDebugThread findThreadById(int id)
{
   LspBaseDebugThread th = thread_data.get(id);
   if (th == null) {
      boolean first = thread_data.isEmpty();
      th = new LspBaseDebugThread(this,id,first);
      thread_data.put(id,th);
    }
   return th;
}

int getId()
{
   return launch_pid;
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

LspBaseDebugProtocol getDebugProtocol()
{
   return debug_protocol;
}



boolean isLocalScope(String scptyp)
{
   if (local_scopes == null) return true;
   return local_scopes.contains(scptyp);
}


boolean isStaticScope(String scptyp)
{
   if (class_scopes == null) return true;
   if (class_scopes.contains(scptyp)) return false;
   return true;
}


boolean isPrimitiveType(String typ) 
{
   if (primitive_types == null || primitive_types.contains(typ)) return true;
   return false;
}


boolean isStringType(String typ) 
{
   if (string_types == null) return false;
   if (string_types.contains(typ)) return true;
   return false;
}



/********************************************************************************/
/*										*/
/*	Start Debugging Methods 						*/
/*										*/
/********************************************************************************/

void startDebug() throws LspBaseException
{
   JSONObject dbgcfg = debug_protocol.getLanguage().getDebugConfiguration();

   String attach = launch_config.getConnectMap();
   launch_error = null;
   is_running = false;
   
   if (use_flutter) {
      if (attach == null) {
         JSONObject args = dbgcfg.getJSONObject("flutterLaunchData");
         args = fixDebugInfo(args);
         debug_protocol.sendJsonRequest("launch",this::debugStarted,args);
       }
      else {
         JSONObject args = dbgcfg.getJSONObject("flutterAttachData");
         args = fixDebugInfo(args);
         debug_protocol.sendJsonRequest("attach",this::debugStarted,args);
       }
    }
   else {
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
    }

   if (launch_error != null) {
      is_terminated = true;
      throw new LspBaseException("Start debug failed: " + launch_error.optString("error"));
    }
   else {
      is_running = true;
      is_terminated = false;
      postProcessEvent("CREATE");
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
	       File wd = launch_config.getWorkingDirectory();
               if (wd != null) sub = wd.getPath();
	       break;
	    case "$ENV" :
	       sub = new JSONObject();
	       sub = null;	// use default for now
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
            case "$DEVICE" :
               // add -d <DEVICE> from launch config
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


void debugAction(LspBaseDebugAction action,String threadid,String frameid,IvyXmlWriter xw)
{
   if (!is_running) return;

   if (threadid == null) {
      switch (action) {
         case TERMINATE :
            if (!is_terminated) {
               debug_protocol.sendRequest("terminate",null,
                     "restart",false);
               xw.textElement("TARGET",action.toString());
             }
            return;
         case RESUME :
            for (LspBaseDebugThread thrd : thread_data.values()) {
               if (thrd.isStopped()) {
                  debug_protocol.sendRequest("continue",null,
                        "threadId",thrd.getId(),"singleThread",false);
                  xw.textElement("TARGET",action.toString());
                  return;
                }
             }
       }
    }
   
   for (LspBaseDebugThread thrd : thread_data.values()) {
      if (matchThread(threadid,thrd)) {
         if (thrd.debugAction(action,frameid)) {
            xw.textElement("THREAD",action.toString());
          }
       }
    }
}
   

private boolean matchThread(String id,LspBaseDebugThread thrd)
{
   if (id == null || id.equals("*")) return true;
   if (id.equals(Integer.toString(thrd.getId()))) return true;
   return false;   
}


JSONObject runInTerminal(JSONObject cmd)
   throws LspBaseException
{
   File cwd = new File(cmd.getString("cwd"));

   JSONArray args = cmd.getJSONArray("args");
   List<String> argl = new ArrayList<>();
   for (int i = 0; i < args.length(); ++i) {
      argl.add(args.getString(i));
    }

   String [] env = null;
   if (!cmd.isNull("env")) {
      JSONObject envmap = cmd.getJSONObject("env");
      int i = 0;
      env = new String[envmap.length()];
      for (Iterator<String> it = envmap.keys(); it.hasNext(); ) {
	 String key = it.next();
	 String val = envmap.getString(key);
	 String txt = key + "=" + val;
	 env[i++] = txt;
       }
    }

   try {
      IvyExec exec = new IvyExec(argl,env,cwd,
	    IvyExec.PROVIDE_INPUT|IvyExec.READ_OUTPUT|IvyExec.READ_ERROR);
      ReaderThread rt0 = new ReaderThread(exec,true);
      rt0.start();
      ReaderThread rt1 = new ReaderThread(exec,false);
      rt1.start();
      cur_exec = exec;

    }
   catch (IOException e) {
      throw new LspBaseException("Exec failed",e);
    }

   return createJson("processId",cur_exec.getPid());
}




/********************************************************************************/
/*                                                                              */
/*     Stack processing                                                         */
/*                                                                              */
/********************************************************************************/

void getStackFrames(int tid,int count,int depth,int arrsz,IvyXmlWriter xw)
{
   for (LspBaseDebugThread thrd : thread_data.values()) {
      if (tid <= 0 || tid == thrd.getId()) {
         xw.begin("THREAD");
	 xw.field("NAME",thrd.getName());
	 xw.field("ID",thrd.getId());
	 xw.field("TARGET",getId());
	 int ctr = 0;
	 for (LspBaseDebugStackFrame frm : thrd.getStackFrames()) {
	    if (frm == null) continue;
	    frm.outputXml(xw,ctr,depth);
	    if (count > 0 && ctr > count) break;
	    ++ctr;
	  }
	 xw.end("THREAD");
       }
    }
}


/********************************************************************************/
/*										*/
/*	Evaluation methods							*/
/*										*/
/********************************************************************************/

void getVariableValue(String tid,String fid,String var,int saveid,int depth,int arr,
      boolean detail,IvyXmlWriter xw)
{
   for (LspBaseDebugThread thrd : thread_data.values()) {
      if (matchThread(tid,thrd)) {
         thrd.getVariableValue(fid,var,saveid,depth,arr,detail,xw);
         break;
       }
    }
}







/********************************************************************************/
/*										*/
/*	Handle run time events							*/
/*										*/
/********************************************************************************/

void processEvent(String event,JSONObject body)
{
   switch (event) {
      case "process" :
	 process_data = new ProcessData(body);
         postProcessEvent("CHANGE");
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
         is_terminated = true;
         for (LspBaseDebugThread dt : thread_data.values()) {
	    dt.handleTerminated();
	  }
	 thread_data.clear();
	 if (process_data != null) process_data.handleTerminated();
	 break;
      case "output" :
	 handleOutput(body);
	 break;
    }
}



/********************************************************************************/
/*										*/
/*	Send updates to Bubbles 						*/
/*										*/
/********************************************************************************/

void postThreadEvent(LspBaseDebugThread thrd,String kind,String detail,String data,boolean iseval)
{
   LspBaseMain lsp = LspBaseMain.getLspMain();
   IvyXmlWriter xw = lsp.beginMessage("RUNEVENT");
   xw.field("TIME",System.currentTimeMillis());
   outputThreadEvent(xw,thrd,kind,detail,data,iseval);
   lsp.finishMessageWait(xw);
}



void postProcessEvent(String kind)
{
   LspBaseMain lsp = LspBaseMain.getLspMain();
   IvyXmlWriter xw = lsp.beginMessage("RUNEVENT");
   xw.field("TIME",System.currentTimeMillis());
   outputProcessEvent(xw,kind);
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


private void outputProcessEvent(IvyXmlWriter xw,String kind) 
{
   xw.begin("RUNEVENT");
   xw.field("KIND",kind);
   xw.field("TYPE","PROCESS");
   outputProcess(xw);
   xw.end("RUNEVENT");
}



/********************************************************************************/
/*										*/
/*	Output methods								*/
/*										*/
/********************************************************************************/

void outputProcess(IvyXmlWriter xw)
{
   xw.begin("PROCESS");
   xw.field("PID",getId());
   outputLaunch(xw);
   xw.end("PROCESS");
}



void outputLaunch(IvyXmlWriter xw)
{
   xw.begin("LAUNCH");
   xw.field("ID",getId());
   xw.field("CID",launch_config.getId());
   xw.field("MODE","debug");
   xw.end("LAUNCH");
}



/********************************************************************************/
/*										*/
/*	Monitor process output							*/
/*										*/
/********************************************************************************/

private void handleOutput(JSONObject body)
{
   String txt = body.getString("output");
   txt = txt.replace("\u2022","*");

   String cat = body.optString("category","console");
   switch (cat) {
      case "console" :
      case "stdout" :
      default :
	 sendConsoleOutput(txt,false);
	 break;
      case "stderr" :
      case "telemetry" :
	 sendConsoleOutput(txt,true);
	 break;
    }
}


private void sendConsoleOutput(String txt,boolean err)
{
   LspBaseMain lsp = LspBaseMain.getLspMain();
   IvyXmlWriter xw = lsp.beginMessage("CONSOLE");
   xw.field("PID",launch_pid);
   xw.field("STDERR",err);
   txt = txt.replace("\010"," ");
   if (txt.length() == 0) return;
   xw.cdataElement("TEXT",txt);
   lsp.finishMessageWait(xw);
   LspLog.logD("Debug Console write " + txt.length());
}

private void sendConsoleEof()
{
   LspBaseMain lsp = LspBaseMain.getLspMain();
   IvyXmlWriter xw = lsp.beginMessage("CONSOLE");
   xw.field("PID",launch_pid);
   xw.field("EOF",true);
   lsp.finishMessageWait(xw);
}


private class ReaderThread extends Thread {

   private BufferedReader input_reader;
   private boolean is_error;

   ReaderThread(IvyExec exec,boolean err) {
      super("Reader_" + (err ? "stderr" : "stdout") + "_" + exec.getPid());
      is_error = err;
      InputStream ins = err ? exec.getErrorStream() : exec.getInputStream();
      input_reader = new BufferedReader(new InputStreamReader(ins));
    }

   @Override public void run() {
      try {
	 for ( ; ; ) {
	    String txt = input_reader.readLine();
	    if (txt == null) break;
	    sendConsoleOutput(txt,is_error);
	  }
       }
      catch (IOException e) { }
      if (!is_error) {
	 sendConsoleEof();
       }
    }

}	// end of inner class ReaderThread



/********************************************************************************/
/*										*/
/*	Process data								*/
/*										*/
/********************************************************************************/

private class ProcessData {

   private String process_name;
   private int system_id;
// private boolean is_local;
// private String launch_type;
// private int pointer_size;
	
   ProcessData(JSONObject body) {
      process_name = body.getString("name");
      system_id = body.optInt("systemProcessId");
//    is_local = body.optBoolean("isLocalProcess",true);
//    launch_type = body.optString("startMethod","launch");
//    pointer_size = body.optInt("pointerSize");
    }

   void handleTerminated() {
      system_id = 0;
      postProcessEvent("TERMINATE");
    }		

   String getName()			{ return process_name; }

   int getId() {
      if (system_id > 0) return system_id;
      return hashCode();
    }

}	// end of inner class ProcessData



}	// end of class LspBaseDebugTarget




/* end of LspBaseDebugTarget.java */

