/********************************************************************************/
/*										*/
/*		LspBaseDebugManager.java					*/
/*										*/
/*	Interface to the debugger						*/
/*										*/
/********************************************************************************/
/*	Copyright 2011 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved		    z			  *
 *										 *
 * This program and the accompanying materials are made available under the	 *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at								 *
 *	http://www.eclipse.org/legal/epl-v10.html				 *
 *										 *
 ********************************************************************************/



package edu.brown.cs.bubbles.lspbase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.ivy.exec.IvyExec;
import edu.brown.cs.ivy.xml.IvyJsonReader;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseDebugManager implements LspBaseConstants
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private LspBaseMain		lsp_base;
private Map<String,LspBaseLaunchConfig> config_map;
private File			config_file;
private File			break_file;
private Map<String,LspBaseBreakpoint> break_map;
private Map<Integer,LspBaseBreakpoint> break_ids;
private Map<String,LspBaseDebugTarget> target_map;
private LspBaseBreakpoint	exception_breakpoint;
private Map<LspBaseDebugTarget,LspBaseDebugProtocol> debug_protocols;
private boolean 		is_started;



/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

LspBaseDebugManager(LspBaseMain nm)
{
   lsp_base = nm;
   config_map = new HashMap<>();
   config_file = new File(lsp_base.getWorkSpaceDirectory(),CONFIG_FILE);
   break_map = new ConcurrentHashMap<>();
   break_file = new File(lsp_base.getWorkSpaceDirectory(),BREAKPOINT_FILE);
   target_map = new ConcurrentHashMap<>();
   exception_breakpoint = null;
   debug_protocols = new WeakHashMap<>();
   break_ids = new ConcurrentHashMap<>();
   is_started = false;
}



void start()
{
   loadConfigurations();
   loadBreakpoints();
   synchronized (this) {
      is_started = true;
      notifyAll();
    }
}


private synchronized void waitForStart()
{
   while (!is_started) {
      try {
	 wait(5000);
       }
      catch (InterruptedException e) { }
    }
}


/********************************************************************************/
/*										*/
/*	Command handler 							*/
/*										*/
/********************************************************************************/

void handleCommand(String cmd,String proj,Element xml,IvyXmlWriter xw)
   throws LspBaseException
{
   waitForStart();

   switch (cmd) {
      case "LAUNCHQUERY" :
	 handleLaunchQuery(proj,
	       IvyXml.getAttrString(xml,"QUERY"),
	       IvyXml.getAttrBool(xml,"OPTION"),xw);
	  break;
      case "GETRUNCONFIG" :
	 getRunConfigurations(xw);
	 break;
      case "NEWRUNCONFIG" :
	 getNewRunConfiguration(proj,
	       IvyXml.getAttrString(xml,"NAME"),
	       IvyXml.getAttrString(xml,"CLONE"),
               IvyXml.getAttrString(xml,"TYPE"),
               IvyXml.getAttrString(xml,"KIND"),xw);
	 break;
      case "EDITRUNCONFIG" :
	 editRunConfiguration(IvyXml.getAttrString(xml,"LAUNCH"),
	       IvyXml.getAttrEnum(xml,"PROP",LspBaseConfigAttribute.NONE),
	       IvyXml.getAttrString(xml,"VALUE"),xw);
	 break;
      case "SAVERUNCONFIG" :
	 saveRunConfiguration(IvyXml.getAttrString(xml,"LAUNCH"),xw);
	 break;
      case "DELETERUNCONFIG" :
	 deleteRunConfiguration(IvyXml.getAttrString(xml,"LAUNCH"),xw);
	 break;

      case "GETALLBREAKPOINTS" :
	 getAllBreakpoints(xw);
	 break;
      case "ADDLINEBREAKPOINT" :
	 setLineBreakpoint(proj,IvyXml.getAttrString(xml,"BID","*"),
	       IvyXml.getTextElement(xml,"FILE"),
	       IvyXml.getAttrInt(xml,"LINE"),
	       IvyXml.getAttrBool(xml,"TRACE",false));
	 break;
      case "ADDEXCEPTIONBREAKPOINT" :
	 setExceptionBreakpoint(proj,IvyXml.getAttrString(xml,"CLASS"),
	       IvyXml.getAttrBool(xml,"CAUGHT",false),
	       IvyXml.getAttrBool(xml,"UNCAUGHT",true),
	       IvyXml.getAttrBool(xml,"CHECKED",false));
	 break;
      case "EDITBREAKPOINT" :
	 editBreakpoint(IvyXml.getAttrString(xml,"ID"),
	       IvyXml.getAttrString(xml,"PROP"),
	       IvyXml.getAttrString(xml,"VALUE"),
	       IvyXml.getAttrString(xml,"PROP1"),
	       IvyXml.getAttrString(xml,"VALUE1"),
	       IvyXml.getAttrString(xml,"PROP2"),
	       IvyXml.getAttrString(xml,"VALUE2"));
	 break;
      case "CLEARALLLINEBREAKPOINTS" :
	 clearLineBreakpoints(proj,null,0);
	 break;
      case "CLEARLINEBREAKPOINT" :
	 clearLineBreakpoints(proj,IvyXml.getAttrString(xml,"FILE"),
	       IvyXml.getAttrInt(xml,"LINE"));
	 break;

      case "START" :
	 runProject(IvyXml.getAttrString(xml,"NAME"),xw);
	 break;
      case "DEBUGACTION" :
	 debugAction(IvyXml.getAttrString(xml,"LAUNCH"),
	       IvyXml.getAttrString(xml,"TARGET"),
	       IvyXml.getAttrString(xml,"PROCESS"),
	       IvyXml.getAttrString(xml,"THREAD"),
	       IvyXml.getAttrString(xml,"FRAME"),
	       IvyXml.getAttrEnum(xml,"ACTION",LspBaseDebugAction.NONE),xw);
	 break;
      case "CONSOLEINPUT" :
	 consoleInput(IvyXml.getAttrString(xml,"LAUNCH"),
	       IvyXml.getTextElement(xml,"INPUT"));
	 break;
      case "GETSTACKFRAMES" :
	 getStackFrames(IvyXml.getAttrString(xml,"LAUNCH"),
	       IvyXml.getAttrInt(xml,"THREAD",0),
	       IvyXml.getAttrInt(xml,"COUNT",-1),
	       IvyXml.getAttrInt(xml,"DEPTH",0),
	       IvyXml.getAttrInt(xml,"ARRAY",100),xw);
	 break;
      case "VARVAL" :
	 getVariableValue(IvyXml.getAttrString(xml,"THREAD"),
	       IvyXml.getAttrString(xml,"FRAME"),
	       IvyXml.getTextElement(xml,"VAR"),
	       IvyXml.getAttrInt(xml,"SAVEID",0),
	       IvyXml.getAttrInt(xml,"DEPTH",1),
	       IvyXml.getAttrInt(xml,"ARRAY",100),false,xw);
	 break;
      case "VARDETAIL" :
	 getVariableValue(IvyXml.getAttrString(xml,"THREAD"),
	       IvyXml.getAttrString(xml,"FRAME"),
	       IvyXml.getTextElement(xml,"VAR"),
	       IvyXml.getAttrInt(xml,"SAVEID",0),
	       IvyXml.getAttrInt(xml,"DEPTH",1),
	       IvyXml.getAttrInt(xml,"ARRAY",100),true,xw);
	 break;

      case "EVALUATE" :
	 evaluateExpression(proj,
	       IvyXml.getAttrString(xml,"BID","*"),
	       IvyXml.getTextElement(xml,"EXPR"),
	       IvyXml.getAttrInt(xml,"THREAD"),
	       IvyXml.getAttrString(xml,"FRAME"),
	       IvyXml.getAttrBool(xml,"IMPLICIT",false),
	       IvyXml.getAttrBool(xml,"BREAK",true),
	       IvyXml.getAttrString(xml,"REPLYID"),
	       IvyXml.getAttrInt(xml,"LEVEL"),
	       IvyXml.getAttrInt(xml,"ARRAY",100),
	       IvyXml.getAttrString(xml,"SAVEID"),
	       IvyXml.getAttrBool(xml,"ALLFRAMES"),xw);
	 break;
      default :
	 throw new LspBaseException("Unknown LSPBASE debug command " + cmd);
    }
}



/********************************************************************************/
/*										*/
/*	Setup debug protocol							*/
/*										*/
/********************************************************************************/

LspBaseDebugProtocol getDebugProtocol(LspBaseDebugTarget tgt)
{
   LspBaseLanguageData ld = tgt.getLanguageData();
   LspBaseDebugProtocol ldp = debug_protocols.get(tgt);
   if (ldp == null) {
      synchronized (debug_protocols) {
	 ldp = debug_protocols.get(tgt);
	 if (ldp == null) {
	    ldp = new LspBaseDebugProtocol(this,tgt,ld);
	    debug_protocols.put(tgt,ldp);
	    try {
	       ldp.initialize();
	     }
	    catch (LspBaseException e) {
	       LspLog.logE("DEBUG: problem initializig protocol",e);
	     }
	  }
       }
    }
   return ldp;
}



void removeDebugProtocol(LspBaseDebugTarget tgt)
{
   synchronized (debug_protocols) {
      debug_protocols.remove(tgt);
    }
}








private void handleLaunchQuery(String proj,String query,boolean option,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseProject lspproj = lsp_base.getProjectManager().findProject(proj);
   
   switch (query) {
      case "START" :
	 getStartFiles(lspproj,option,xw);
	 break;
      case "DEVICE" :
	 getDevices(lspproj,xw);
	 break;
    }
}



private void getStartFiles(LspBaseProject lspproj,boolean lib,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseProtocol proto = lspproj.getProtocol();
   MainFinder mf = new MainFinder(lspproj,lib,xw);
   proto.sendWorkMessage("workspace/symbol",mf,"query","main");
}


private class MainFinder implements LspArrayResponder {

   private boolean use_library;
   private LspBaseProject for_project;
   private IvyXmlWriter xml_writer;

   MainFinder(LspBaseProject lbp,boolean lib,IvyXmlWriter xw) {
      for_project = lbp;
      use_library = lib;
      xml_writer = xw;
    }

   @Override public void handleResponse(JSONArray arr) {
      for (int i = 0; i < arr.length(); ++i) {
         JSONObject sym = arr.getJSONObject(i);
         String nm = sym.getString("name");
         int idx = nm.indexOf("(");
         if (idx > 0) nm = nm.substring(0,idx);
         if (!nm.equals("main")) continue;
         if (sym.getInt("kind") != 12) continue;
         String uri = sym.getJSONObject("location").getString("uri");
         File f = null;
         if (!use_library) {
            LspBaseFile lbf = for_project.findFile(uri);
            if (lbf == null) continue;
            f = lbf.getFile();
          }
         else {
            if (uri.startsWith("file:/")) {
               int j = 0;
               for (int k = 5; i < uri.length(); ++k) {
        	  if (uri.charAt(k) == '/') j = k;
        	  else break;
        	}
               uri = uri.substring(j);
             }
            f = new File(uri);
            if (!f.exists()) continue;
          }
         xml_writer.begin("OPTION");
         xml_writer.field("VALUE",f.getPath());
         xml_writer.end("OPTION");
       }
    }

}	// end of inner class MainFinder



private void getDevices(LspBaseProject proj,IvyXmlWriter xw)
   throws LspBaseException
{
   try {
      IvyExec exec = new IvyExec("flutter devices --machine",
	    IvyExec.READ_OUTPUT|IvyExec.ERROR_OUTPUT);
      InputStream ins = exec.getInputStream();
      IvyJsonReader jr = new IvyJsonReader(ins);
      String jsonstr = jr.readJson();
      JSONArray arr = new JSONArray(jsonstr);
      for (int i = 0; i < arr.length(); ++i) {
	 JSONObject dev = arr.getJSONObject(i);
	 if (!dev.optBoolean("isSupported",true)) continue;
	 String nm = dev.getString("name");
	 if (dev.optBoolean("emulator")) nm += " (Emulator)";
	 xw.begin("OPTION");
	 xw.field("DISPLAY",nm);
	 xw.field("VALUE",dev.getString("id"));
	 xw.end("OPTION");
       }
      jr.close();
      exec.destroy();
    }
   catch (IOException e) {
      throw new LspBaseException("Problem getting devices",e);
    }
}



/********************************************************************************/
/*										*/
/*	Run configuration methods						*/
/*										*/
/********************************************************************************/

void getNewRunConfiguration(String proj,String name,String clone,String type,String kind,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseLaunchConfig plc = null;
   if (clone != null) {
      LspBaseLaunchConfig orig = config_map.get(clone);
      if (orig == null)
	 throw new LspBaseException("Configuration to clone not found: " + clone);
      if (name == null) name = getUniqueName(orig.getName());
      plc = new LspBaseLaunchConfig(name,orig);
    }
   else {
      LspBaseProject lspproj = lsp_base.getProjectManager().findProject(proj);
      if (name == null) name = getUniqueName("New Launch");
      plc = new LspBaseLaunchConfig(lspproj,name,type,kind);
    }

   if (plc != null) {
      if (proj != null) plc.setAttribute(LspBaseConfigAttribute.PROJECT_ATTR,proj);
      config_map.put(plc.getId(),plc);
      plc.outputBubbles(xw);
      handleLaunchNotify(plc,"ADD");
    }
}


void editRunConfiguration(String id,LspBaseConfigAttribute prop,String value,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseLaunchConfig cfg = config_map.get(id);
   if (cfg == null) throw new LspBaseException("Launch configuration " + id + " not found");

   cfg = cfg.getWorkingCopy();
   if (prop == LspBaseConfigAttribute.NAME) {
      cfg.setName(value);
    }
   else {
      cfg.setAttribute(prop,value);
    }

   if (xw != null) cfg.outputBubbles(xw);

   handleLaunchNotify(cfg,"CHANGE");
}


void saveRunConfiguration(String id,IvyXmlWriter xw) throws LspBaseException
{
   LspBaseLaunchConfig cfg = config_map.get(id);
   if (cfg == null) throw new LspBaseException("Launch configuration " + id + " not found");

   cfg.commitWorkingCopy();

   saveConfigurations();

   if (xw != null) cfg.outputBubbles(xw);

   handleLaunchNotify(cfg,"CHANGE");
}


void deleteRunConfiguration(String id,IvyXmlWriter xw)
{
   LspBaseLaunchConfig cfg = config_map.remove(id);
   if (cfg == null) return;
   cfg.setSaved(false);
   saveConfigurations();

   handleLaunchNotify(cfg,"REMOVE");
}



void getRunConfigurations(IvyXmlWriter xw)
{
   for (LspBaseLaunchConfig plc : config_map.values()) {
      plc.outputBubbles(xw);
    }
}



private void loadConfigurations()
{
   Element xml = IvyXml.loadXmlFromFile(config_file);
   for (Element le : IvyXml.children(xml,"LAUNCH")) {
      LspBaseLaunchConfig plc = new LspBaseLaunchConfig(le);
      config_map.put(plc.getId(),plc);
    }
}




/********************************************************************************/
/*										*/
/*	Breakpoint methods							*/
/*										*/
/********************************************************************************/

Iterable<LspBaseBreakpoint> getBreakpoints()
{
   return break_map.values();
}

LspBaseBreakpoint findBreakpoint(int id)
{
   return break_ids.get(id);
}


LspBaseBreakpoint findBreakpoint(String exception)
{
   StringTokenizer tok = new StringTokenizer(exception," (");
   String etyp = tok.nextToken();
   
   LspBaseBreakpoint dflt = null;
   for (LspBaseBreakpoint pb : break_map.values()) {
      if (pb.getType() == BreakType.EXCEPTION) {
         if (pb.getException() == null || pb.getException().isEmpty()) dflt = pb; 
         else if (pb.getException().equals(etyp)) return pb;
       }
    }
   return dflt;
}




void getAllBreakpoints(IvyXmlWriter xw)
{
   xw.begin("BREAKPOINTS");
   for (LspBaseBreakpoint pb : break_map.values()) {
      pb.outputBubbles(xw);
    }
   xw.end("BREAKPOINTS");
}



void setLineBreakpoint(String proj,String bid,String file,int line,boolean trace)
   throws LspBaseException
{
   LspBaseFile nf = lsp_base.getFileData(proj,file);

   for (LspBaseBreakpoint prev : break_map.values()) {
      if (prev.getType() == BreakType.LINE &&
	    prev.getFile().equals(nf) &&
	    prev.getLine() == line) {
	 return;
       }
    }

   LspBaseBreakpoint pb = LspBaseBreakpoint.createLineBreakpoint(nf,line);
   break_map.put(pb.getId(),pb);
   handleBreakNotify(pb,"ADD");
   updateBreakpointsForFile(nf);
   saveBreakpoints();
}



void setExceptionBreakpoint(String proj,String name,
      boolean caught,boolean uncaught,boolean checked)
   throws LspBaseException
{
   LspBaseProject lbp = null;
   if (proj != null) {
      lbp = lsp_base.getProjectManager().findProject(proj);
    }

   if (exception_breakpoint == null && name == null) {
      exception_breakpoint = LspBaseBreakpoint.createExceptionBreakpoint(lbp,null,
	    caught,uncaught);
      break_map.put(exception_breakpoint.getId(),exception_breakpoint);
      handleBreakNotify(exception_breakpoint,"ADD");
    }
   else if (name == null) {
      exception_breakpoint.setProperty("CAUGHT",Boolean.toString(caught));
      exception_breakpoint.setProperty("UNCAUGHT",Boolean.toString(uncaught));
      handleBreakNotify(exception_breakpoint,"CHANGE");
    }
   else {
      LspBaseBreakpoint pb = LspBaseBreakpoint.createExceptionBreakpoint(lbp,name,caught,uncaught);
      break_map.put(pb.getId(),pb);
      saveBreakpoints();
      handleBreakNotify(pb,"ADD");
    }

   updateExceptionBreakpoints(lbp);
   saveBreakpoints();
}


void editBreakpoint(String id,String ... pv)
   throws LspBaseException
{
   LspBaseBreakpoint bp = break_map.get(id);
   if (bp == null) throw new LspBaseException("Breakpoint " + id + " not found");

   for (int i = 0; i < pv.length; i += 2) {
      String p = pv[i];
      String v = pv[i+1];
      if (p == null) continue;
      if (p.equals("CLEAR")) {
	 bp.clear();
	 break_map.remove(id);
	 if (bp == exception_breakpoint) exception_breakpoint = null;
	 handleBreakNotify(bp,"REMOVE");
	 break;
       }
      else bp.setProperty(p,v);
    }

   switch (bp.getType()) {
      case LINE :
	 updateBreakpointsForFile(bp.getFile());
	 break;
      case DATA :
	 break;
      case EXCEPTION :
	 updateExceptionBreakpoints(bp.getProject());
	 break;
      case FUNCTION :
	 break;
      case NONE :
	 break;
    }

   handleBreakNotify(bp,"CHANGE");
}


void handleBreakpointEvent(JSONObject body)
{
   JSONObject jbpt = body.getJSONObject("breakpoint");
   String reason = body.getString("reason");
   switch (reason) {
      case "new" :
	 reason = "ADD";
	 break;
      case "removed" :
	 reason = "REMOVE";
	 break;
      case "changed" :
      default :
	 reason = "CHANGE";
	 break;
    }
   int id = jbpt.getInt("id");
   LspBaseBreakpoint bpt = break_ids.get(id);
   if (bpt == null) {
      if (reason.equals("REMOVE")) return;              // already removed
      bpt = LspBaseBreakpoint.createBreakpoint(jbpt);
      if (bpt == null) return;
    }

   if (reason.equals("REMOVE")) {
      break_map.remove(bpt.getId());
      break_ids.remove(id);
    }
   else {
      LspBaseBreakpoint obpt = break_map.get(bpt.getId());
      if (obpt == null) {
	 reason = "ADD";
       }
      else if (obpt != bpt) {
	 obpt.setProtoInfo(jbpt);
	 break_ids.put(id,obpt);
	 bpt = obpt;
	 reason = "CHANGE";
       }
    }

   handleBreakNotify(bpt,reason);
}




void clearLineBreakpoints(String proj,String file,int line)
{
   LspBaseFile nf = null;
   if (file != null) nf = lsp_base.getFileData(proj,file);
   LspLog.logD("DEBUG: CLEAR BPTS " + file + " " + line + " " + nf);

   Set<LspBaseFile> updates = new HashSet<>();
   for (Iterator<LspBaseBreakpoint> it = break_map.values().iterator(); it.hasNext(); ) {
      LspBaseBreakpoint bp = it.next();
      if (bp.getType() == BreakType.LINE) {
	 if (nf == null || bp.getFile().equals(nf)) {
	    if (line <= 0 || line == bp.getLine()) {
	       it.remove();
	       handleBreakNotify(bp,"REMOVE");
	       updates.add(bp.getFile());
	     }
	  }
       }
    }
   if (!updates.isEmpty()) {
      for (LspBaseFile lbf : updates) {
	 try {
	    updateBreakpointsForFile(lbf);
	  }
	 catch (LspBaseException e) {
	    LspLog.logE("DEBUG problem clearing breakpoints",e);
	  }
       }
      saveBreakpoints();
    }
}



private void loadBreakpoints()
{
   Element xml = null;
   if (break_file.length() > 10) {
      xml = IvyXml.loadXmlFromFile(break_file);
    }
   if (xml == null) {
      exception_breakpoint = LspBaseBreakpoint.createExceptionBreakpoint(null,null,
	    false,true);
      break_map.put(exception_breakpoint.getId(),exception_breakpoint);
      saveBreakpoints();
    }
   else {
      for (Element be : IvyXml.children(xml,"BREAKPOINT")) {
	 try {
	    LspBaseBreakpoint pb = LspBaseBreakpoint.createBreakpoint(be);
	    if (pb == null) continue;
	    switch (pb.getType()) {
	       case NONE :
		  break;
	       case EXCEPTION :
		  if (!pb.isCaught() && !pb.isUncaught()) {
		     if (exception_breakpoint != null) {
			break_map.remove(exception_breakpoint.getId());
			exception_breakpoint = null;
		      }
		     pb = null;
		   }
		  else if (exception_breakpoint == null) {
		     exception_breakpoint = pb;
		   }
		  else {
		     exception_breakpoint.setProperty("CAUGHT",Boolean.toString(pb.isCaught()));
		     exception_breakpoint.setProperty("UNCAUGHT",Boolean.toString(pb.isUncaught()));
		     pb = null;
		   }
		  break;
	       case LINE :
		  for (LspBaseBreakpoint prev : break_map.values()) {
		     if (prev.getType() == BreakType.LINE &&
			   prev.getFile().equals(pb.getFile()) &&
			   prev.getLine() == pb.getLine()) {
			pb = null;
			break;
		      }
		   }
		  break;
	     }
	    if (pb != null) {
	       break_map.put(pb.getId(),pb);
	     }
	  }
	 catch (LspBaseException e) {
	    LspLog.logE("Debug: Breakpoint not found: " + IvyXml.convertXmlToString(xml),e);
	  }
       }
    }
}



private void saveBreakpoints()
{
   try {
      IvyXmlWriter xw = new IvyXmlWriter(break_file);
      xw.begin("BREAKPOINTS");
      for (LspBaseBreakpoint pb : break_map.values()) {
	 pb.outputXml(xw);
       }
      xw.end("BREAKPOINTS");
      xw.close();
    }
   catch (IOException e) {
      LspLog.logE("Debug: Problem writing out breakpoints",e);
    }
}



private void handleBreakNotify(LspBaseBreakpoint pb,String reason)
{
   IvyXmlWriter xw = lsp_base.beginMessage("BREAKEVENT");
   xw.begin("BREAKPOINTS");
   xw.field("REASON",reason);
   pb.outputBubbles(xw);
   xw.end("BREAKPOINTS");
   lsp_base.finishMessage(xw);
}




void updateAllBreakpoints(LspBaseDebugProtocol proto)
      throws LspBaseException
{
   Set<LspBaseFile> files = new HashSet<>();
   for (LspBaseBreakpoint pb : break_map.values()) {
      LspBaseFile lbf = pb.getFile();
      if (lbf != null) {
	 if (lbf.getLanguageData() == proto.getLanguage()) {
	    files.add(lbf);
	  }
       }
    }
   for (LspBaseFile lbf : files) {
      updateBreakpointsForFile(lbf);
    }

   updateExceptionBreakpoints(proto);
}


private void updateBreakpointsForFile(LspBaseFile lbf) throws LspBaseException
{
   JSONObject src = createJson("path",lbf.getFile().getPath());
   JSONArray bpts = new JSONArray();
   List<LspBaseBreakpoint> use = new ArrayList<>();
   for (LspBaseBreakpoint bp : break_map.values()) {
      if (bp.getFile() == lbf && bp.getType() == BreakType.LINE) {
	 LineCol lc = bp.getLineColumn();
	 JSONObject jbpt = createJson("line",lc.getLine(),"column",lc.getColumn());
//	 JSONObject jbpt = createJson("line",lc.getLine());
	 if (bp.getCondition() != null) {
	    jbpt.put("condition",bp.getCondition());
	  }
	 if (bp.getHitCondition() != null) {
	    jbpt.put("hitCondition",bp.getHitCondition());
	  }
	  if (bp.getTraceLog() != null) {
	     jbpt.put("logMessage",bp.getTraceLog());
	   }
	  use.add(bp);
	  bpts.put(jbpt);
       }
    }
   for (LspBaseDebugProtocol proto : debug_protocols.values()) {
      if (proto.getLanguage() == lbf.getLanguageData()) {
         try {
            BreakpointsSet setter = new BreakpointsSet(use);
            proto.sendEarlyRequest("setBreakpoints",setter,
                  "source",src,"breakpoints",bpts,"sourceModified",true);
          }
         catch (Throwable e) { }
       }
    }
}



private void updateExceptionBreakpoints(LspBaseProject proj)
      throws LspBaseException
{
   for (LspBaseDebugProtocol proto : debug_protocols.values()) {
      if (proj == null || proj.getLanguageData() == proto.getLanguage()) {
	 updateExceptionBreakpoints(proto);
       }
    }
}


private void updateExceptionBreakpoints(LspBaseDebugProtocol proto)
      throws LspBaseException
{
   JSONArray filters = new JSONArray();
   JSONArray exceptopts = new JSONArray();

   LspBaseLanguageData ld = proto.getLanguage();
   String filtercp = "lsp.exceptionBreakpoints.filter.";

   List<LspBaseBreakpoint> use = new ArrayList<>();
   for (LspBaseBreakpoint bp : break_map.values()) {
      if (bp.getType() == BreakType.EXCEPTION) {
	 String filter = null;
	 String mode = null;
	 if (bp.isCaught() && bp.isUncaught()) {
	    filter = ld.getCapabilityString(filtercp + "always");
	    mode = "always";
	  }
	 else if (bp.isUncaught()) {
	    filter = ld.getCapabilityString(filtercp + "uncaught");
	    mode = "userUnhandled";
	  }
	 else if (bp.isCaught()) {
	    filter = ld.getCapabilityString(filtercp + "caught");
	    mode = "always";
	  }
	 if (filter == null || mode == null) continue;

	 JSONObject exceptopt = createJson("breakMode",mode);
	 String except = bp.getException();
	 if (except != null) {
	    JSONArray names = new JSONArray();
	    names.put(except);
	    exceptopt.put("path",createJson("names",names));
	  }
	 filters.put(filter);
	 exceptopts.put(exceptopt);
	 use.add(bp);
       }
    }

   if (use.isEmpty()) return;

   BreakpointsSet setter = new BreakpointsSet(use);
   proto.sendEarlyRequest("setExceptionBreakpoints",setter,
	 "filters",filters,"exceptionOptions",exceptopts);
}



private class BreakpointsSet implements LspJsonResponder {

   private List<LspBaseBreakpoint> break_points;

   BreakpointsSet(List<LspBaseBreakpoint> bpts) {
      break_points = bpts;
    }

   @Override public void handleResponse(JSONObject data) {
      JSONArray bpts = data.optJSONArray("breakpoints");
      if (bpts != null) {
	 for (int i = 0; i < break_points.size(); ++i) {
	    JSONObject jdata = bpts.getJSONObject(i);
	    LspBaseBreakpoint bp = break_points.get(i);
	    LspLog.logD("Debug: SET BREAK DATA " + bp + " " + jdata.toString(2));
	    bp.setProtoInfo(jdata);
	    if (bp.getExternalId() > 0) {
	       break_ids.put(bp.getExternalId(),bp);
	     }
	  }
       }
    }
}



/********************************************************************************/
/*										*/
/*	Debugger action methods 						*/
/*										*/
/********************************************************************************/

void runProject(String configid,IvyXmlWriter xw) throws LspBaseException
{
   LspBaseLaunchConfig cfg = config_map.get(configid);
   if (cfg == null) throw new LspBaseException("Configuration not found");

   LspBaseDebugTarget tgt = new LspBaseDebugTarget(this,cfg);
   tgt.startDebug();

   target_map.put(Integer.toString(tgt.getId()),tgt);

   xw.begin("LAUNCH");
   xw.field("MODE","debug");
   xw.field("ID",tgt.getId());
   xw.field("CID",cfg.getId());
   xw.field("TARGET",tgt.getId());
   xw.field("PROCESS",tgt.getId());
   xw.end("LAUNCH");
}


void debugAction(String launchid,String targetid,
      String procid,String threadid,String frameid,
      LspBaseDebugAction action,IvyXmlWriter xw)
   throws LspBaseException
{
   for (LspBaseDebugTarget tgt : target_map.values()) {
      if (!matchLaunch(launchid,tgt)) continue;
      if (!matchLaunch(targetid,tgt)) continue;
      tgt.debugAction(action,threadid,frameid,xw);
    }
}


private boolean matchLaunch(String id,LspBaseDebugTarget tgt)
{
   if (id == null || id.equals("*")) return true;
   int tid = Integer.parseInt(id);
   if (tid == 0 || tgt.getId() == tid) return true;
   return false;
}


void consoleInput(String launch,String input)
{

}




/********************************************************************************/
/*										*/
/*	Value methods								*/
/*										*/
/********************************************************************************/

void getStackFrames(String launchid,int tid,int count,int depth,int arrsz,IvyXmlWriter xw)
   throws LspBaseException
{
   int lid = 0;
   if (launchid != null) {
      try {
	 lid = Integer.parseInt(launchid);
       }
      catch (NumberFormatException e) { }
    }

   xw.begin("STACKFRAMES");
   for (LspBaseDebugTarget tgt : target_map.values()) {
      if (launchid != null && tgt.getId() != lid) continue;
      tgt.getStackFrames(tid,count,depth,arrsz,xw);
    }
   xw.end("STACKFRAMES");
}


void getVariableValue(String thread,String frame,String var,int saveid,int depth,int arr,
      boolean detail,IvyXmlWriter xw)
      throws LspBaseException
{
   for (LspBaseDebugTarget tgt : target_map.values()) {
      tgt.getVariableValue(thread,frame,var,saveid,depth,arr,
	    detail,xw);
    }

}


void evaluateExpression(String proj,String bid,String expr,int thread,
      String frame,boolean implicit,boolean stop,String eid,
      int lvl,int arr,String saveid,boolean allframes,
      IvyXmlWriter xw)
   throws LspBaseException
{
   boolean done = false;
   int fidx = -1;
   if (frame != null) {
      fidx = Integer.parseInt(frame);
    }

   // need to do this in background if eid is not null

   for (LspBaseDebugTarget tgt : target_map.values()) {
      LspBaseDebugThread thrd = tgt.findThreadById(thread);
      if (thrd != null) {
	 for (LspBaseDebugStackFrame frm : thrd.getStackFrames()) {
	    if (frm == null) continue;
	    if (fidx < 0 || fidx == frm.getIndex()) {
	       LspBaseMain lsp = LspBaseMain.getLspMain();
	       IvyXmlWriter msg  = lsp.beginMessage("EVALUATION",bid);
	       msg.field("ID",eid);
	       ExprEvaluator ee = new ExprEvaluator(frm,expr,msg);
	       lsp.startTask(ee);
	       return;
	     }
	  }
       }
      if (done) break;
    }

   if (!done) throw new LspBaseException("No evaluation to do");
}


private class ExprEvaluator implements Runnable {

   LspBaseDebugStackFrame for_frame;
   String eval_expr;
   IvyXmlWriter xml_writer;

   ExprEvaluator(LspBaseDebugStackFrame frm,String expr,IvyXmlWriter msg) {
      for_frame = frm;
      eval_expr = expr;
      xml_writer = msg;
    }

   @Override public void run() {
      LspBaseMain lsp = LspBaseMain.getLspMain();
      try {
	 LspBaseDebugVariable rslt = for_frame.evaluateExpression(eval_expr);
	 xml_writer.field("SAVEID",rslt.getReference());
	 rslt.outputValue(xml_writer);
       }
      catch (LspBaseException e) {
	 LspLog.logE("DEBUG evaluation problem",e);
       }
      lsp.finishMessage(xml_writer);
    }

}	// end of inner class ExprEvaluator




/********************************************************************************/
/*										*/
/*	Helper methods								*/
/*										*/
/********************************************************************************/

private String getUniqueName(String nm)
{
   int idx = nm.lastIndexOf("(");
   if (idx >= 0) nm = nm.substring(0,idx).trim();
   for (int i = 1; ; ++i) {
      String nnm = nm + " (" + i + ")";
      boolean fnd = false;
      for (LspBaseLaunchConfig plc : config_map.values()) {
	 if (plc.getName().equals(nnm)) fnd = true;
       }
      if (!fnd) return nnm;
    }
}



private void handleLaunchNotify(LspBaseLaunchConfig plc,String reason)
{
   IvyXmlWriter xw = lsp_base.beginMessage("LAUNCHCONFIGEVENT");
   xw.begin("LAUNCH");
   xw.field("REASON",reason);
   xw.field("ID",plc.getId());
   if (plc != null) plc.outputBubbles(xw);
   xw.end("LAUNCH");
   lsp_base.finishMessage(xw);
}



private void saveConfigurations()
{
   try {
      IvyXmlWriter xw = new IvyXmlWriter(config_file);
      xw.begin("CONFIGS");
      for (LspBaseLaunchConfig plc : config_map.values()) {
	 if (plc.isSaved()) {
	    plc.outputSaveXml(xw);
	  }
       }
      xw.end("CONFIGS");
      xw.close();
    }
   catch (IOException e) {
      LspLog.logE("Debug: Problem writing out configurations",e);
    }
}



}	// end of class LspBaseDebugManager




/* end of LspBaseDebugManager.java */

