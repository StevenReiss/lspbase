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
import java.util.List;
import java.util.Map;
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
private String client_id;
private List<File> work_spaces;
private List<File> source_roots;
private boolean is_initialized;
private boolean doing_initialization;
private LspBaseLanguageData for_language;
private Writer message_stream;
private Map<String,File> pathWorkspaceMap;
private Map<File,List<LspBasePathSpec>> workspacePathMap;
private JSONObject config_data;



private static AtomicInteger id_counter = new AtomicInteger(10000);
private static AtomicLong progress_counter = new AtomicLong(1);


static final int ParseError = -32700;
static final int InvalidRequest = -32600;
static final int MethodNotFound = -32601;
static final int InvalidParams = -32602;
static final int internalError = -32603;
static final int jsonrpcReservedErrorRangeStart = -32099;
static final int serverErrorStart= jsonrpcReservedErrorRangeStart;
static final int ServerNotInitialized = -32002;
static final int UnknownErrorCode = -32001;
static final int jsonrpcReservedErrorRangeEnd = -32000;
static final int serverErrorEnd = jsonrpcReservedErrorRangeEnd;
static final int lspReservedErrorRangeStart = -32899;
static final int RequestFailed = -32803;
static final int ServerCancelled = -32802;
static final int ContentModified = -32801;
static final int RequestCancelled = -32800;
static final int lspReservedErrorRangeEnd = -32800;



/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

LspBaseProtocol(File workspace,List<LspBasePathSpec> paths,LspBaseLanguageData ld)
{
   pending_map = new HashMap<>();
   pathWorkspaceMap = new HashMap<>();
   workspacePathMap = new HashMap<>();

   for_language = ld;
   client_id = ld.getName() + "_" + workspace.getName();
   
   String rname = "lspbase-" + for_language.getName() + ".json";
   String json = null;
   try (InputStream ins = LspBaseProtocol.class.getClassLoader().getResourceAsStream(rname)) {
      if (ins != null) json = IvyFile.loadFile(ins);
    }
   catch (IOException e) { }
   config_data = null;
   try {
      config_data = new JSONObject(json);
      for_language.setCapabilities(config_data.getJSONObject("lspbaseConfiguration"));
    }
   catch (Throwable e) {
      LspLog.logE("Problem with capability json: " + e);
    }
   
   String command = ld.getExecString();
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
      if (path.isExclude() || !path.isUser()) continue;
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


void shutDown()
{
   if (!is_initialized) return;

   localSendMessage("shutdown",null,true,null);
   localSendMessage("exit",null,false,null);
}




void initialize()
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

   JSONObject clientcaps = config_data.getJSONObject("initialConfiguration");

   JSONObject obj = new JSONObject();
   obj.put("workdoneToken","INITIALIZING");
   obj.put("processId",IvyExecQuery.getProcessNumber().intValue());
   obj.put("clientInfo",createJson("name","bubbles_lsp","version","0.1"));
   if (!source_roots.isEmpty()) {
      obj.put("rootUri",getUri(source_roots.get(0)));
    }
   obj.put("capabilities",clientcaps);
   obj.put("trace","verbose");
   if (!for_language.isSingleWorkspace()) {
      List<JSONObject> ws = new ArrayList<>();
      for (File f : source_roots) {
	 JSONObject wsf = createJson("uri",getUri(f),"name",f.getName());
	 ws.add(wsf);
       }
      obj.put("workspaceFolders",ws);
    }
   localSendMessage("initialize",this::handleInit,true,obj);

   localSendMessage("initialized",null,true,new JSONObject());

   synchronized (this) {
      doing_initialization = false;
      is_initialized = true;
      notifyAll();
    }

   System.err.println("finished initialization");
}



private void handleInit(Object resp,JSONObject err)
{
   if (err != null) {
      return;
    }

   JSONObject init = (JSONObject) resp;
   JSONObject scaps = init.getJSONObject("capabilities");
   for_language.setCapabilities(scaps);
}



/********************************************************************************/
/*                                                                              */
/*      JSON creation methods                                                   */
/*                                                                              */
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

void sendMessage(String method,LspResponder resp,Object ... params)
{
   JSONObject obj = createJson(params);
   sendJson(method,resp,obj);
}


void sendJson(String method,LspResponder resp,JSONObject params)
{
   if (!is_initialized) initialize();

   localSendMessage(method,resp,true,params);
}




private void localSendMessage(String method,LspResponder resp,boolean wait,JSONObject params)
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

   if (wait && resp == null) resp = this::dummyHandler;

   if (resp != null) pending_map.put(id,resp);

   synchronized (message_stream) {
      try{
	 message_stream.write(buf.toString());
	 message_stream.flush();
       }
      catch (IOException e) {
	 LspLog.logE("Problem writing message",e);
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


private void dummyHandler(Object resp,JSONObject err)
{
   if (err != null) {
      LspLog.logE("Unexpected error response: " + err);
    }
}












void processReply(int id,Object cnts)
{
   LspResponder lsp = pending_map.get(id);

   String s = (cnts == null ? null : cnts.toString());
   if (cnts instanceof JSONObject) {
      JSONObject jcnts = (JSONObject) cnts;
      s = jcnts.toString(2);
    }
   else if (cnts instanceof JSONArray) {
      JSONArray jcnts = (JSONArray) cnts;
      s = jcnts.toString(2);
    }
   LspLog.logD("Reply: " + id + " " + (lsp != null) + " " + s);
   
   if (lsp != null) {
      lsp.handleResponse(cnts,null);
    }
   synchronized (this) {
      pending_map.remove(id);
      notifyAll();
    }
}


void processError(int id,JSONObject err)
{
   LspLog.logE("Process Error " + err.toString(2));
   
   LspResponder lsp = pending_map.remove(id);
   if (lsp != null) lsp.handleResponse(null,err);
}


void processNotification(Integer id,String method,Object params)
{
   JSONObject jparams = null;
 
   String s = (params == null ? null : params.toString());
   if (params instanceof JSONObject) {
      jparams = (JSONObject) params;
      s = jparams.toString(2);
    }
   LspLog.logD("Notification: " + method + " " + s);
   
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
      default :
         LspLog.logE("Unknown notification " + method);
         break;
    }
   
   localSendResponse(id,result,null);
}


Object handleWorkspaceConfiguration(int id,JSONObject params)
{
   JSONObject clientconfig = config_data.getJSONObject("clientConfiguration");
   
   JSONArray items = params.getJSONArray("items");
   JSONArray result = new JSONArray();
   JSONArray exclude = new JSONArray();
   for (int i = 0; i < items.length(); ++i) {
      JSONObject itm = items.getJSONObject(i);
      String scp = itm.optString("scopeUri");
      String section = itm.optString("section");
      if (scp != null) {
         File ws = pathWorkspaceMap.get(scp);
         if (ws != null) {
            for (LspBasePathSpec lbps : workspacePathMap.get(ws)) {
               if (lbps.isExclude()) {
                  String path = lbps.getFile().getPath();
                  exclude.put(path);
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
   
   String kind = val.getString("kind");
   String ttl = val.optString("title");
   
   kind = kind.toUpperCase();
   if (kind.equals("END")) kind = "DONE";
   
   LspBaseMain lsp = LspBaseMain.getLspMain();
   IvyXmlWriter xw = lsp.beginMessage("PROGRESS");
   xw.field("KIND",kind.toUpperCase());
   xw.field("TASK",ttl);
   xw.field("ID",token);
   xw.field("S",progress_counter.incrementAndGet());
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
   
   IvyXmlWriter xw = lsp.beginMessage("FILEERROR");
   xw.field("PROJECT",lbf.getProject().getName());
   xw.field("FILE",lbf.getPath());
   if (version > 0) xw.field("ID",version);
   xw.begin("MESSAGES");
   for (int i = 0; i < diags.length(); ++i) {
      LspBaseUtil.outputDiagnostic(lbf,diags.getJSONObject(i),xw);
    }
   xw.end("MESSAGES");
   lsp.finishMessage(xw);
}




/********************************************************************************/
/*										*/
/*	Message reader								*/
/*										*/
/********************************************************************************/

private class MessageReader extends Thread {

   private BufferedInputStream message_input;

   MessageReader(InputStream input) {
      super("LSP_Message_Reader_ " + client_id);
      message_input = new BufferedInputStream(input);
    }

   @Override public void run() {
      int clen = -1;
      for ( ; ; ) {
         try {
            String ln = readline();
            if (ln == null) break;
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
   //             LspLog.logD("Received: " + clen + " " + rln + "::\n" + jobj.toString(2));
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
    }
   
   String readline() throws IOException {
      byte [] buf = new byte[10000];
      int ln = 0;
      int lastb = 0;
      for ( ; ; ) {
         int b = message_input.read();
         if (b == '\n' && lastb == '\r') break;
         buf[ln++] = (byte) b;
         lastb = b;
       }
      if (ln > 0 && buf[ln-1] == '\r') --ln;
      return new String(buf,0,ln);
    }

   void process(JSONObject reply) {
      int id = reply.optInt("id");
      String method = reply.optString("method");
      JSONObject err = reply.optJSONObject("error");
      if (err != null) {
         processError(id,err);
       }
      else if (id == 0 || pending_map.get(id) == null) {
         if (method != null) {
            Object params = reply.opt("params");
            processNotification(id,method,params);
          }
         else {
            LspLog.logE("Problem with message " + reply.toString(2));
          }
       }
      else {
         processReply(id,reply.opt("result"));
       }
    }


}	// end of inner class MessageReader




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






}	// end of class LspBaseProtocol




/* end of LspBaseProtocol.java */

