/********************************************************************************/
/*										*/
/*		LspBaseProtocol.java						*/
/*										*/
/*	Implementation of protocol to talk to LSP server			*/
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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.brown.cs.ivy.exec.IvyExec;
import edu.brown.cs.ivy.exec.IvyExecQuery;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseProtocol implements LspBaseConstants
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private Map<Integer,LspResponder> pending_map;
private Map<Integer,String> error_map;
private String client_id;
private List<File> work_spaces;
private List<File> source_roots;
private boolean is_initialized;
private boolean doing_initialization;
private LspBaseLanguageData for_language;
private Writer message_stream;
private Map<String,File> pathWorkspaceMap;
private Map<File,List<LspBasePathSpec>> workspacePathMap;
private Set<String> active_progress;



private static AtomicInteger id_counter = new AtomicInteger(100000);
private static AtomicLong progress_counter = new AtomicLong(1);


/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

LspBaseProtocol(File workspace,List<LspBasePathSpec> paths,LspBaseLanguageData ld)
{
   pending_map = new ConcurrentHashMap<>();
   error_map = new ConcurrentHashMap<>();
   pathWorkspaceMap = new HashMap<>();
   workspacePathMap = new HashMap<>();
   active_progress = new HashSet<>();

   for_language = ld;
   client_id = ld.getName() + "_" + workspace.getName();

   String command = ld.getLspExecString();
   Map<String,String> keys = new HashMap<>();
   keys.put("ID","LspBase_" + ld.getName() + "_" + workspace.getName());

   command = IvyFile.expandName(command,keys);
   LspLog.logD("Run server: " + command);

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

   is_initialized = false;
   doing_initialization = false;

   work_spaces = new ArrayList<>();
   source_roots = new ArrayList<>();

   addWorkspace(workspace,paths);
}


void addWorkspace(File ws,List<LspBasePathSpec> paths) {
   List<File> sources = new ArrayList<>();

   for (LspBasePathSpec path : paths) {
      if (!path.isUser()) continue;
      File f = path.getFile();
      if (!source_roots.contains(f)) {
	 sources.add(f);
	 pathWorkspaceMap.put(getUri(f),ws);
       }
    }
   List<LspBasePathSpec> oldpaths = workspacePathMap.get(ws);
   if (oldpaths == null) workspacePathMap.put(ws,new ArrayList<>(paths));
   else oldpaths.addAll(paths);

   synchronized (this) {
      while (doing_initialization) {
	 try {
	    wait(5000);
	  }
	 catch (InterruptedException e) { }
       }
      if (!is_initialized) {
	 work_spaces.add(ws);
	 source_roots.addAll(sources);
	 return;
       }
    }


   // send message to add workspace
   work_spaces.add(ws);
   source_roots.addAll(sources);
}



/********************************************************************************/
/*										*/
/*	Initialization and shut down						*/
/*										*/
/********************************************************************************/

void shutDown()
{
   if (!is_initialized) return;

   try {
      localSendMessage("shutdown",true,null);
      localSendMessage("exit",false,null);
    }
   catch (LspBaseException e) { }
}




void initialize() throws LspBaseException
{
   synchronized (this) {
      while (doing_initialization) {
	 try {
	    wait(5000);
	  }
	 catch (InterruptedException e) { }
       }
      if (is_initialized) return;
      doing_initialization = true;
    }

   JSONObject clientcaps = for_language.getLspConfiguration();

   JSONObject obj = new JSONObject();
   obj.put("workdoneToken","INITIALIZING");
   obj.put("processId",IvyExecQuery.getProcessNumber().intValue());
   obj.put("clientInfo",createJson("name","bubbles_lsp","version","0.1"));
   if (!source_roots.isEmpty()) {
      obj.put("rootUri",getUri(source_roots.get(0)));
    }
   obj.put("capabilities",clientcaps);
   obj.put("trace","messages");
   if (!for_language.isSingleWorkspace()) {
      List<JSONObject> ws = new ArrayList<>();
      for (File f : source_roots) {
	 JSONObject wsf = createJson("uri",getUri(f),"name",f.getName());
	 ws.add(wsf);
       }
      obj.put("workspaceFolders",ws);
    }
   localSendMessage("initialize",this::handleInit,true,obj);

   localSendMessage("initialized",true,new JSONObject());

   synchronized (this) {
      doing_initialization = false;
      is_initialized = true;
      notifyAll();
    }

   LspLog.logD("Finished initialization");
}


void waitForProgressDone(String id)
{
   synchronized (active_progress) {
      for ( ; ; ) {
	 if (id == null && active_progress.isEmpty()) break;
	 if (id != null && !active_progress.contains(id)) break;
	 try {
	    active_progress.wait(1000);
	  }
	 catch (InterruptedException e) { }
       }
    }
}


private void handleInit(JSONObject init)
{
   JSONObject scaps = init.getJSONObject("capabilities");
   for_language.setCapabilities(null,scaps);
}



/********************************************************************************/
/*										*/
/*	JSON creation methods							*/
/*										*/
/********************************************************************************/

JSONObject createRange(LspBaseFile file,int start,int end)
{
   return createJson("start",createPosition(file,start),
	 "end",createPosition(file,end));
}


JSONObject createPosition(LspBaseFile file,int pos)
{
   LineCol lc = file.mapOffsetToLineColumn(pos);
   return createJson("line",lc.getLspLine(),"character",lc.getLspColumn());
}



/********************************************************************************/
/*										*/
/*	Message sending 							*/
/*										*/
/********************************************************************************/

void sendMessage(String method,LspJsonResponder resp,Object ... params)
      throws LspBaseException
{
   JSONObject obj = createJson(params);
   sendJson(method,resp,obj);
}


void sendMessage(String method,Object ... params)
   throws LspBaseException
{
   JSONObject obj = createJson(params);
   sendJson(method,obj);
}


void sendMessage(String method,LspArrayResponder resp,Object ... params)
   throws LspBaseException
{
   JSONObject obj = createJson(params);
   sendJson(method,resp,obj);
}


void sendMessage(String method,LspAnyResponder resp,Object ... params)
   throws LspBaseException
{
   JSONObject obj = createJson(params);
   sendJson(method,resp,obj);
}


String sendWorkMessage(String method,LspJsonResponder resp,Object ... params)
      throws LspBaseException
{
   String tok = "WORK_" + progress_counter.getAndIncrement();
   JSONObject obj = createJson(params);
   obj.put("workDoneToken",tok);
   sendJson(method,resp,obj);
   return tok;
}


String sendWorkMessage(String method,LspArrayResponder resp,Object ... params)
   throws LspBaseException
{
   String tok = "WORK_" + progress_counter.getAndIncrement();
   JSONObject obj = createJson(params);
   obj.put("workDoneToken",tok);
   sendJson(method,resp,obj);
   return tok;
}


String sendWorkMessage(String method,LspAnyResponder resp,Object ... params)
   throws LspBaseException
{
   String tok = "WORK_" + progress_counter.getAndIncrement();
   JSONObject obj = createJson(params);
   obj.put("workDoneToken",tok);
   sendJson(method,resp,obj);
   return tok;
}


void sendJson(String method,LspJsonResponder resp,JSONObject params)
      throws LspBaseException
{
   if (!is_initialized) initialize();

   localSendMessage(method,resp,true,params);
}


void sendJson(String method,LspAnyResponder resp,JSONObject params)
   throws LspBaseException
{
   if (!is_initialized) initialize();

   localSendMessage(method,resp,true,params);
}


void sendJson(String method,JSONObject params)
   throws LspBaseException
{
   if (!is_initialized) initialize();

   localDoSendMessage(method,null,true,params);
}


void sendJson(String method,LspArrayResponder resp,JSONObject params)
   throws LspBaseException
{
   if (!is_initialized) initialize();

   localSendMessage(method,resp,true,params);
}




private void localSendMessage(String method,LspJsonResponder resp,boolean wait,JSONObject params)
      throws LspBaseException
{
   if (wait && resp == null) resp = this::dummyHandler;

   localDoSendMessage(method,resp,wait,params);
}


private void localSendMessage(String method,LspAnyResponder resp,boolean wait,JSONObject params)
   throws LspBaseException
{
   if (wait && resp == null) resp = this::dummyHandler;

   localDoSendMessage(method,resp,wait,params);
}


private void localSendMessage(String method,boolean wait,JSONObject params)
   throws LspBaseException
{
   LspJsonResponder resp = this::dummyHandler;

   localDoSendMessage(method,resp,wait,params);
}


private void localSendMessage(String method,LspArrayResponder resp,boolean wait,JSONObject params)
   throws LspBaseException
{
   localDoSendMessage(method,resp,wait,params);
}


private void localDoSendMessage(String method,LspResponder resp,boolean wait,JSONObject params)
   throws LspBaseException
{
   int id = id_counter.getAndIncrement();
   JSONObject jo = new JSONObject();
   jo.put("jsonrpc","2.0");
   jo.put("id",id);
   if (method.contains(".")) jo.put("id",Integer.toString(id));
   jo.put("method",method);
   if (params != null) jo.put("params",params);
   String cnts = jo.toString();
   int len = cnts.length();
   StringBuffer buf = new StringBuffer();
   buf.append("Content-Length: ");
   buf.append(len);
   buf.append("\r\n");
   buf.append("\r\n");
   buf.append(cnts);

   LspLog.logD("Send: " + id + " " + method + " " + jo.toString(2));

   synchronized (this) {
      if (resp != null) {
         pending_map.put(id,resp);
       }
      if (!wait) error_map.put(id,"");
    }
   
   synchronized (message_stream) {
      try{
	 message_stream.write(buf.toString());
	 message_stream.flush();
       }
      catch (IOException e) {
	 LspLog.logE("Problem writing message",e);
       }
    }

   String err = null;
   if (wait && resp != null) {
      synchronized(this) {
	 while (pending_map.containsKey(id)) {
	    try {
	       wait(5000);
	     }
	    catch (InterruptedException e) { }
	  }
	 err = error_map.remove(id);
	 if (err != null && err.equals("")) err = null;
       }
    }

   if (err != null) throw new LspBaseException(err);
}


private void localSendResponse(int id,Object result,JSONObject err)
{
   if (id <= 0) return;

   JSONObject jo = new JSONObject();
   jo.put("jsonrpc","2.0");
   jo.put("id",id);
   if (err == null) jo.put("result",result);
   else jo.put("error",err);
   String cnts = jo.toString();
   int len = cnts.length();
   StringBuffer buf = new StringBuffer();
   buf.append("Content-Length: ");
   buf.append(len);
   buf.append("\r\n");
   buf.append("\r\n");
   buf.append(cnts);

   LspLog.logD("Send Response: " + id  + " " + jo.toString(2));

   synchronized (message_stream) {
      try{
	 message_stream.write(buf.toString());
	 message_stream.flush();
       }
      catch (IOException e) {
	 LspLog.logE("Problem writing response",e);
       }
    }
}


private void dummyHandler(Object resp)	    { }



void processReply(int id,Object cnts)
{
   LspResponder lsp = pending_map.get(id);

   try {
      if (lsp != null && lsp instanceof LspJsonResponder) {
	 LspJsonResponder jlsp = (LspJsonResponder) lsp;
	 JSONObject jobj;
	 if (cnts == JSONObject.NULL) jobj = new JSONObject();
	 else jobj = (JSONObject) cnts;
	 jlsp.handleResponse(jobj);
      }
      else if (lsp != null && lsp instanceof LspArrayResponder) {
	 JSONArray jcnts;
	 if (cnts == JSONObject.NULL || cnts == null) jcnts = new JSONArray();
	 else jcnts = (JSONArray) cnts;
	 LspArrayResponder alsp = (LspArrayResponder) lsp;
	 alsp.handleResponse(jcnts);
       }
      else if (lsp != null && lsp instanceof LspAnyResponder) {
	 LspAnyResponder alsp = (LspAnyResponder) lsp;
	 if (cnts == JSONObject.NULL) cnts = null;
	 alsp.handleResponse(cnts);
       }
   }
   catch (Throwable t) {
      LspLog.logE("Problem processing response",t);
    }
   finally {
      synchronized (this) {
	 pending_map.remove(id);
	 notifyAll();
      }
   }
}


void processError(int id,JSONObject err)
{
   LspLog.logE("Process Error " + id + " " + err.toString(2));

   LspResponder lsp = pending_map.get(id);
   String er = error_map.get(id);
   try {
      if (lsp != null && er != null && er.equals("")) {
	 error_map.remove(id);
	 if (lsp instanceof LspJsonResponder) {
	    LspJsonResponder jlsp = (LspJsonResponder) lsp;
	    jlsp.handleResponse(null);
	  }
	 else if (lsp instanceof LspArrayResponder) {
	    LspArrayResponder alsp = (LspArrayResponder) lsp;
	    alsp.handleResponse(null);
	  }
       }
      String msg = err.optString("error","Protocol error");
      error_map.put(id,msg);
    }
   catch (Throwable t) {
      LspLog.logE("Problem processing error response",t);
    }
   finally {
      synchronized (this) {
	 pending_map.remove(id);
	 notifyAll();
       }
    }
}


void processNotification(Integer id,String method,Object params)
{
   JSONObject jparams = null;

   String s = (params == null ? null : params.toString());
   if (params instanceof JSONObject) {
      jparams = (JSONObject) params;
      s = jparams.toString(2);
    }
   LspLog.logD("Notification: " + method + " " + id + " "+ s);
   synchronized (this) {
      if (pending_map.remove(id) != null) {
         notifyAll();
       }
    }

   Object result = null;
   switch (method) {
      case "workspace/configuration" :
	 result = handleWorkspaceConfiguration(id,jparams);
	 break;
      case "window/workDoneProgress/create" :
	 result = handleWorkDoneProgressCreate(id,jparams);
	 break;
      case "$/progress" :
	 handleProgress(id,jparams);
	 break;
      case "textDocument/publishDiagnostics" :
	 handlePublishDiagnostics(id,jparams);
	 break;
      case "window/logMessage" :
	 handleLogMessage(id,jparams);
	 break;
      default :
	 LspLog.logE("Unknown notification " + method);
	 break;
    }

   localSendResponse(id,result,null);
}


Object handleWorkspaceConfiguration(int id,JSONObject params)
{
   JSONObject clientconfig = for_language.getClientConfiguration();

   JSONArray items = params.getJSONArray("items");
   JSONArray result = new JSONArray();
   JSONArray exclude = new JSONArray();
   for (int i = 0; i < items.length(); ++i) {
      JSONObject itm = items.getJSONObject(i);
      String scp = itm.optString("scopeUri",null);
      String section = itm.optString("section");
      if (scp != null) {
	 File ws = pathWorkspaceMap.get(scp);
	 if (ws != null) {
	    for (LspBasePathSpec lbps : workspacePathMap.get(ws)) {
	       for (String s: lbps.getExcludes()) {
		  exclude.put(s);
		}
	     }
	  }
       }
      JSONObject sectionconfig = clientconfig.optJSONObject(section);
      if (sectionconfig != null) addExcludes(sectionconfig,exclude);
      result.put(sectionconfig);
    }

   return result;
}


private void addExcludes(JSONObject obj,JSONArray exc)
{
   for (String key : obj.keySet()) {
      switch (key) {
	 case "analysisExcludedFolder" :
	    obj.put(key,exc);
	    break;
	 default :
	    Object val = obj.get(key);
	    if (val instanceof JSONObject) {
	       addExcludes((JSONObject) val,exc);
	     }
	    break;
       }
    }
}


Object handleWorkDoneProgressCreate(int id,JSONObject params)
{
   // handleProgress takes care of this
   return null;
}


void handleProgress(int id,JSONObject params)
{
   String token = params.getString("token");
   JSONObject val = params.getJSONObject("value");
   LspLog.logD("PROGRESS VALUE " + val.toString(2));

   double pct = 0;
   String kind = val.getString("kind");
   switch (kind) {
      case "begin" :
	 pct = 0;
	 synchronized (active_progress) {
	    active_progress.add(token);
	  }
	 break;
      case "end" :
	 pct = 1.0;
	 synchronized (active_progress) {
	    active_progress.remove(token);
	    active_progress.notifyAll();
	  }
	 break;
    }
   String ttl = val.optString("title",null);
   if (ttl != null) ttl = ttl.replace("\u2026","...");

   kind = kind.toUpperCase();
   if (kind.equals("END")) kind = "DONE";

   LspBaseMain lsp = LspBaseMain.getLspMain();
   IvyXmlWriter xw = lsp.beginMessage("PROGRESS");
   xw.field("KIND",kind.toUpperCase());
   if (ttl != null) xw.field("TASK",ttl);
   xw.field("ID",token);
   xw.field("S",progress_counter.incrementAndGet());
   xw.field("WORK",pct);
   lsp.finishMessage(xw);
}


void handlePublishDiagnostics(int id,JSONObject params)
{
   LspBaseMain lsp = LspBaseMain.getLspMain();
   String uri = params.getString("uri");
   LspBaseFile lbf = lsp.getProjectManager().findFile(null,uri);
   if (lbf == null) return;
   int version = params.optInt("version",-1);
   JSONArray diags = params.getJSONArray("diagnostics");

   IvyXmlWriter xw;
   int pdx = uri.indexOf(PRIVATE_PREFIX);
   if (pdx < 0) {
      xw = lsp.beginMessage("FILEERROR");
      if (version > 0) xw.field("ID",version);
    }
   else {
      pdx += PRIVATE_PREFIX.length();
      int pidx1 = uri.lastIndexOf(".");
      String pid = uri.substring(pdx,pidx1);
      xw = lsp.beginMessage("PRIVATEERROR");
      xw.field("ID",pid);
    }

   xw.field("PROJECT",lbf.getProject().getName());
   xw.field("FILE",lbf.getPath());

   xw.begin("MESSAGES");
   for (int i = 0; i < diags.length(); ++i) {
      LspBaseUtil.outputDiagnostic(lbf,diags.getJSONObject(i),xw);
    }
   xw.end("MESSAGES");
   lsp.finishMessage(xw);
}


void handleLogMessage(int id,JSONObject params)
{
   int type = params.getInt("type");
   String msg = params.getString("message");
   switch (type) {
      case 1 :
	 LspLog.logE("LSPPROTO: " + msg);
	 break;
      case 2 :
	 LspLog.logW("LSPPROTO: " + msg);
	 break;
      case 3 :
	 LspLog.logI("LSPPROTO: " + msg);
	 break;
      default :
      case 4 :
	 LspLog.logD("LSPPROTO: " + msg);
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
   private byte [] byte_buf;

   MessageReader(InputStream input) {
      super("LSP_Message_Reader_" + client_id);
      message_input = new BufferedInputStream(input);
      byte_buf = null;
    }

   @Override public void run() {
      int clen = -1;
      for ( ; ; ) {
	 try {
	    String ln = readline();
	    if (ln == null) {
               ln = readline();
               if (ln == null) break;
             }
//          LspLog.logD("Header line: " + clen + " " + ln.length() + ln);
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
// 		  LspLog.logD("Received: " + clen + " " + rln + "::\n" + jobj.toString(2));
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
	 catch (IOException e) { }
       }
      LspLog.logE("END OF FILE RECEIVED FROM INPUT READER " + byte_buf);
   }

   String readline() throws IOException {
      byte_buf = new byte[10000];
      int ln = 0;
      int lastb = 0;
      for ( ; ; ) {
	 int b = message_input.read();
	 if (b == '\n' && lastb == '\r') break;
         if (b == -1) {
            LspLog.logD("EOF FROM input stream " + ln);
            for (int j = 0; j < ln; ++j) {
               char ch = (char) byte_buf[j];
               LspLog.logD("   CHAR " + j + " " + ch);
             }
          }
	 byte_buf[ln++] = (byte) b;
	 lastb = b;
       }
      if (ln > 0 && byte_buf[ln-1] == '\r') --ln;
      return new String(byte_buf,0,ln);
    }

   void process(JSONObject reply) {
      // might want a single message processor rather than separate threads
      MessageProcessor mp = new MessageProcessor(reply);
      LspBaseMain lsp = LspBaseMain.getLspMain();
      lsp.startTask(mp);
    }


}	// end of inner class MessageReader




private class MessageProcessor implements Runnable {
   
   private JSONObject reply_json;
   
   MessageProcessor(JSONObject reply) {
      reply_json = reply;
    }
   
   @Override public void run() {
      int id = reply_json.optInt("id");
      String method = reply_json.optString("method",null);
      JSONObject err = reply_json.optJSONObject("error");
      if (err != null) {
         processError(id,err);
       }
      else if (id == 0 || pending_map.get(id) == null) {
         if (method != null) {
            Object params = reply_json.opt("params");
            processNotification(id,method,params);
          }
         else {
            String rslt = reply_json.optString("result",null);
            if (rslt != null) {
               LspLog.logE("Problem with message " + reply_json.toString(2));
             }
            else {
               LspLog.logD("Process unused message " + reply_json.toString(2));
             }
          }
       }
      else {
         LspLog.logD("Handle reply: " + id + "\n" + reply_json.toString(2));
         processReply(id,reply_json.opt("result"));
       }
    }
}
private class ErrorReader extends Thread {

   private BufferedReader input_reader;

   ErrorReader(InputStream ist) {
      super("LSP_Error_Reader_" + client_id);
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






}	// end of class LspBaseProtocol




/* end of LspBaseProtocol.java */

