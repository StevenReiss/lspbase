/********************************************************************************/
/*                                                                              */
/*              LspBaseDebugProtocol.java                                       */
/*                                                                              */
/*      Communications with DAP server for debugging                            */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2011 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.                            *
 *                                                                               *
 *                        All Rights Reserved                                    *
 *                                                                               *
 *  Permission to use, copy, modify, and distribute this software and its        *
 *  documentation for any purpose other than its incorporation into a            *
 *  commercial product is hereby granted without fee, provided that the          *
 *  above copyright notice appear in all copies and that both that               *
 *  copyright notice and this permission notice appear in supporting             *
 *  documentation, and that the name of Brown University not be used in          *
 *  advertising or publicity pertaining to distribution of the software          *
 *  without specific, written prior permission.                                  *
 *                                                                               *
 *  BROWN UNIVERSITY DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS                *
 *  SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND            *
 *  FITNESS FOR ANY PARTICULAR PURPOSE.  IN NO EVENT SHALL BROWN UNIVERSITY      *
 *  BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY          *
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,              *
 *  WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS               *
 *  ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE          *
 *  OF THIS SOFTWARE.                                                            *
 *                                                                               *
 ********************************************************************************/



package edu.brown.cs.bubbles.lspbase;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONObject;

import edu.brown.cs.ivy.exec.IvyExec;
import edu.brown.cs.ivy.file.IvyFile;

class LspBaseDebugProtocol implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

enum InitState {
   UNINIT, 
   START_INIT,
   INIT_RECEIVED,
   INIT_EVENT, 
   DONE_SENT, 
   INITIALIZED,
};
   
private LspBaseDebugTarget debug_target;
private Map<Integer,LspResponder> pending_map;
private String client_id;
private InitState init_state;
private LspBaseLanguageData for_language;
private Writer message_stream;
private JSONObject config_data;
private LspBaseDebugManager debug_manager;

private static AtomicInteger id_counter = new AtomicInteger(50000);
private static AtomicLong progress_counter = new AtomicLong(1);


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseDebugProtocol(LspBaseDebugManager mgr,LspBaseDebugTarget tgt,LspBaseLanguageData ld)
{
   debug_manager = mgr;
   debug_target = tgt;
   pending_map = new HashMap<>();
   for_language = ld;
   config_data = for_language.getDebugConfiguration();
   
   String nm = LspBaseMain.getLspMain().getWorkSpaceDirectory().getName();
   client_id = ld.getName() + "_LSP_" + nm;          
        
   String command = ld.getDapExecString();
   if (command == null) return;
   
   Map<String,String> keys = new HashMap<>();
   command = IvyFile.expandName(command,keys);
   LspLog.logD("DEBUG: Run server: " + command);
   
   try {
      IvyExec exec = new IvyExec(command,IvyExec.PROVIDE_INPUT | IvyExec.READ_OUTPUT | IvyExec.READ_ERROR);
      InputStream rdr = exec.getInputStream();
      InputStream err = exec.getErrorStream();
      message_stream = new OutputStreamWriter(exec.getOutputStream());
      MessageReader mr = new MessageReader(rdr);
      mr.start();
      ErrorReader er = new ErrorReader(err);
      er.start();
    }
   catch (IOException e) { }
   
   init_state = InitState.UNINIT;
}



/********************************************************************************/
/*                                                                              */
/*      Initialization                                                          */
/*                                                                              */
/********************************************************************************/

void initialize()
{
   synchronized (this) {
      if (init_state != InitState.UNINIT) {
         waitForInitialization();
         return;
       }
      init_state = InitState.START_INIT;
    }
   
   sendInitialize();
}


private void sendInitialize()
{
   JSONObject args = config_data.getJSONObject("initialize");
   args.put("clientID",client_id);
   localSendRequest("initialize",this::handleInit,true,args);
}



void waitForInitialization() 
{
   if (init_state == InitState.INITIALIZED) return;
   if (init_state == InitState.UNINIT) initialize();
   
   synchronized (this) {
      while (init_state != InitState.INITIALIZED) {
         try {
            wait(5000);
          }
         catch (InterruptedException e) { }
       }
    }
}


void waitForPreInitialization() 
{
   if (init_state == InitState.INITIALIZED) return;
   
   synchronized (this) {
      if (init_state == InitState.UNINIT) {
         init_state = InitState.START_INIT;
         sendInitialize();
       }
    }
   
   synchronized (this) {
      for ( ; ; ) {
         switch (init_state) {
            case INITIALIZED :
            case DONE_SENT :
            case INIT_EVENT :
               return;
            case INIT_RECEIVED :
            case START_INIT :
               break;
          }
         try {
            wait(5000);
          }
         catch (InterruptedException e) { }
       }
    }
}


private void handleInit(Object resp,JSONObject err)
{
   JSONObject caps = (JSONObject) resp;
   for_language.setCapabilities("debug",caps);
   
   LspLog.logD("DEBUG: Received capabilities " + caps.toString(2));
   
   init_state = InitState.INIT_RECEIVED;
}


private void handleInitialized()
{
   init_state = InitState.INIT_EVENT; 
   
   LspLog.logD("DEBUG: initialized event");
   
   debug_manager.updateAllBreakpoints(this);
   
   init_state = InitState.DONE_SENT;
   
   if (for_language.getCapabilityBool("debug.supportsConfigurationDoneRequest")) {
      localSendRequest("configurationDone",null,true,new JSONObject());
    }
   
   synchronized (this) {
      init_state = InitState.INITIALIZED;
      notifyAll();
    }
   
   LspLog.logD("DEBUG: Finished initialization");
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

LspBaseLanguageData getLanguage()
{
   return for_language;
}



/********************************************************************************/
/*                                                                              */
/*      Request sending                                                         */
/*                                                                              */
/********************************************************************************/

void sendRequest(String method,LspResponder resp,Object ... params)
{
   JSONObject obj = createJson(params);
   sendJsonRequest(method,resp,obj);
}


void sendEarlyRequest(String method,LspResponder resp,Object ... params)
{
   JSONObject obj = createJson(params);
   
   waitForPreInitialization();
   
   localSendRequest(method,resp,true,obj);
}


String sendWorkRequest(String method,LspResponder resp,Object ... params)
{
   String tok = "WORK_" + progress_counter.getAndIncrement();
   JSONObject obj = createJson(params);
   obj.put("workDoneToken",tok);
   sendJsonRequest(method,resp,obj);
   return tok;
}

void sendJsonRequest(String method,LspResponder resp,JSONObject params)
{
   waitForInitialization();
   
   localSendRequest(method,resp,true,params);
}



private void localSendRequest(String method,LspResponder resp,boolean wait,JSONObject params)
{
   int id = id_counter.getAndIncrement();
   JSONObject jo = new JSONObject();
   jo.put("seq",id);
   jo.put("type","request");
   jo.put("command",method);
   if (params != null) jo.put("arguments",params);
   
   String cnts = jo.toString();
   int len = cnts.length();
   StringBuffer buf = new StringBuffer();
   buf.append("Content-Length: ");
   buf.append(len);
   buf.append("\r\n");
   buf.append("\r\n");
   buf.append(cnts);
   
   LspLog.logD("DEBUG: Send " + id + " " + method + " " + len + " " + jo.toString(2));
   
   if (wait && resp == null) resp = this::dummyHandler;
   
   if (resp != null) pending_map.put(id,resp);
   
   synchronized (message_stream) {
      try{
	 message_stream.write(buf.toString());
	 message_stream.flush();
       }
      catch (IOException e) {
	 LspLog.logE("DEBUG: Problem writing message",e);
       }
    }
   
   if (wait && resp != null) {
      synchronized(this) {
	 while (pending_map.containsKey(id)) {
	    try {
	       wait(5000);
	     }
	    catch (InterruptedException e) { }
	  }
       }
    }
}



/********************************************************************************/
/*                                                                              */
/*      Response sending                                                        */
/*                                                                              */
/********************************************************************************/

private void sendResponse(int seq,String cmd,JSONObject resp,String error)
{
   JSONObject jo = new JSONObject();
   jo.put("request_seq",seq);
   jo.put("success",(error == null));
   jo.put("type","request");
   jo.put("command",cmd);
   if (error != null) jo.put("message",error);
   if (resp != null) jo.put("body",resp);
   
   String cnts = jo.toString();
   int len = cnts.length();
   StringBuffer buf = new StringBuffer();
   buf.append("Content-Length: ");
   buf.append(len);
   buf.append("\r\n");
   buf.append("\r\n");
   buf.append(cnts);
   
   LspLog.logD("DEBUG: Response " + seq + " " + cmd + " " + len + " " + jo.toString(2));
   
   synchronized (message_stream) {
      try{
	 message_stream.write(buf.toString());
	 message_stream.flush();
       }
      catch (IOException e) {
	 LspLog.logE("DEBUG: Problem writing response message",e);
       }
    }
}




private void dummyHandler(Object resp,JSONObject err)
{
   if (err != null) {
      LspLog.logE("DEBUG: Unexpected error response: " + err);
    }
}


/********************************************************************************/
/*                                                                              */
/*      Message processing                                                      */
/*                                                                              */
/********************************************************************************/

void processResponse(JSONObject resp)
{
   int id = resp.optInt("request_seq");
   LspResponder lsp = pending_map.get(id);
   LspLog.logD("DEBUG: Reply " + id + " " + (lsp != null) + " " + resp.toString(2));
   
   try {
      if (resp.getBoolean("success")) {
         Object cnts = resp.opt("body");
         if (lsp != null) {
            lsp.handleResponse(cnts,null);
          }
       } 
      else {
        if (lsp != null) {
           lsp.handleResponse(null,resp);
         }
       }
    }
   catch (Throwable t) {
      LspLog.logE("DEBUG: Problem processing response",t);
    }
   finally {
      synchronized (this) {
	 pending_map.remove(id);
	 notifyAll();
       }
    } 
}


void processRequest(JSONObject req)
{
   int seq = req.getInt("seq");
   String cmd = req.getString("command");
   JSONObject args = req.optJSONObject("arguments");
   
   switch (cmd) {
      case "runInTerminal" :
         try {
            JSONObject resp = debug_target.runInTerminal(args);
            sendResponse(seq,cmd,resp,null);
          }
         catch (LspBaseException e) {
            sendResponse(seq,cmd,null,e.getMessage());
          }
         break;
    }
   
}


void processEvent(JSONObject resp)
{ 
   String event = resp.getString("event");
   Object data = resp.opt("body");
   JSONObject body = null;
   if (data instanceof JSONObject) body = (JSONObject) data;
// int id = resp.getInt("seq");
   int idx = event.indexOf(".");
   if (idx > 0) { 
      event = event.substring(idx+1);
    }
   switch (event) {
      case "initialized" :
         handleInitialized();
         break; 
      case "breakpoint" :
         debug_manager.handleBreakpointEvent(body);
         break;
      case "capabilities" :
         JSONObject caps = body.getJSONObject("capabilities");
         for_language.setCapabilities("debug",caps);
         break;
      case "process" :
      case "thread" :
      case "stopped" :
      case "continued" :
      case "output" :
      case "terminated" :
         debug_target.processEvent(event,body);
         break;
      case "exited" :
         break;
      case "module" :
         break;
      case "invalidated" :
         break;
      case "loadedSource" :
         break;
      case "memory" :
         break;
      case "progressEnd" :
         break;
      case "progressStart" :
         break;
      case "progressUpdated" :
         break;
      case "appStart" :
      case "appStarted" :
      case "debuggerUris" :
      case "serviceRegistered" :
      case "serviceExtensionAdded" :
      case "serviceExtensionStateChanged" :
         break;
      case "log" :
         LspLog.logI("DEBUG: " + body.getString("message"));
         break;
      default :
         LspLog.logE("DEBUG: Unknown event " + resp.toString(2));
         break;
    }
}



/********************************************************************************/
/*										*/
/*	Message reader								*/
/*										*/
/********************************************************************************/

private class MessageReader extends Thread {
   
   private BufferedInputStream message_input;
   
   MessageReader(InputStream input) {
      super("LSP_Debug_Message_Reader_" + client_id);
      message_input = new BufferedInputStream(input);
    }
   
   @Override public void run() {
      int clen = -1;
      for ( ; ; ) {
         try {
            String ln = readline();
            if (ln == null) break;
            LspLog.logD("DEBUG: Read line: " + ln);
            if (ln.length() == 0) {
               if (clen > 0) {
                  byte [] buf = new byte[clen];
                  int rln = 0;
                  while (rln < clen) {
                     int mln = message_input.read(buf,rln,clen-rln);
                     rln += mln;
                   }
                  String rslt = new String(buf,0,rln);
                  JSONObject jobj = new JSONObject(rslt);
                  LspLog.logD("DEBUG: Received " + clen + " " + rln + "::\n" + jobj.toString(2));
                  process(jobj);
                  clen = -1;
                }
             }
            else {
               int idx = ln.indexOf(":");
               if (idx >= 0) {
                  String key = ln.substring(0,idx).trim();
                  String val = ln.substring(idx+1).trim();
                  if (key.equalsIgnoreCase("Content-Length")) {
                     clen = Integer.parseInt(val);
                   }
                }
             }
          }
         catch (IOException e) {
            LspLog.logE("DEBUG: Problem reading debug message",e);
          }
       }
      LspLog.logI("DEBUG: message reader exited");
    }
   
   String readline() throws IOException {
      byte [] buf = new byte[100000];
      int ln = 0;
      int lastb = 0;
      for ( ; ; ) {
         int b = message_input.read();
         if (b == -1) return null;
         if (b == '\n' && lastb == '\r') break;
         buf[ln++] = (byte) b;
         lastb = b;
       }
      if (ln > 0 && buf[ln-1] == '\r') --ln;
      return new String(buf,0,ln);
    }
   
   void process(JSONObject reply) {
      MessageProcessor mp = new MessageProcessor(reply);
      String type = reply.getString("type");
      // might want to not process events if we are processing a response actively?
      if (type.equals("event")) {
         // process events asynchronously as they might generate messages
         LspBaseMain lsp = LspBaseMain.getLspMain();
         lsp.startTask(mp);
       }
      else {
         // process responses and requests synchronously
         mp.run();
       }
    }
   
}	// end of inner class MessageReader



class MessageProcessor implements Runnable {
   
   JSONObject json_message;
   
   MessageProcessor(JSONObject msg) {
      json_message = msg;
    }
   
   @Override public void run() {
      LspLog.logD("Debug process " + json_message.toString(2));
      
      String type = json_message.getString("type");
      switch (type) {
         case "response" :
            processResponse(json_message);
            break;
         case "event" :
            processEvent(json_message);
            break;
         case "request" :
            processRequest(json_message);
            break;
         default :
            LspLog.logE("DEBUG: Unexpected message of type " + type + " " + json_message.toString(2));
            break;
       }
    }
   
}






private class ErrorReader extends Thread {
   
   private BufferedReader input_reader;
   
   ErrorReader(InputStream ist) {
      super("LSP_ERROR_READER_" + client_id);
      input_reader = new BufferedReader(new InputStreamReader(ist));
    }
   
   @Override public void run() {
      try {
         for ( ; ; ) {
            String l = input_reader.readLine();
            if (l == null) break;
            LspLog.logE("Protocol error: " + l);
          }
       }
      catch (IOException e) { return; }
    }
   
}	// end of subclass ReaderThread



}       // end of class LspBaseDebugProtocol




/* end of LspBaseDebugProtocol.java */

