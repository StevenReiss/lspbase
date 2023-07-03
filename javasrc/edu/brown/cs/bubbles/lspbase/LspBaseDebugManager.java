/********************************************************************************/
/*                                                                              */
/*              LspBaseDebugManager.java                                        */
/*                                                                              */
/*      Interface to the debugger                                               */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2011 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.                            *
 *                                                                               *
 *                        All Rights Reserved               z                     *
 *                                                                               *
 * This program and the accompanying materials are made available under the      *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at                                                           *
 *      http://www.eclipse.org/legal/epl-v10.html                                *
 *                                                                               *
 ********************************************************************************/



package edu.brown.cs.bubbles.lspbase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseDebugManager implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private LspBaseMain		lsp_base;
private Map<String,LspBaseLaunchConfig> config_map;
private File			config_file;
private File			break_file;
private Map<String,LspBaseBreakpoint> break_map;
private Map<String,LspBaseDebugTarget> target_map;
private LspBaseBreakpoint	exception_breakpoint;
private Map<String,LspBaseDebugProtocol> debug_protocols;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
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
   debug_protocols = new HashMap<>();
   
   loadConfigurations();
   loadBreakpoints();
}



/********************************************************************************/
/*                                                                              */
/*      Command handler                                                         */
/*                                                                              */
/********************************************************************************/

void handleCommand(String cmd,String proj,Element xml,IvyXmlWriter xw)
   throws LspBaseException
{
   switch (cmd) {
      case "GETRUNCONFIG" :
         getRunConfigurations(xw);
         break;
      case "NEWRUNCONFIG" :
         getNewRunConfiguration(proj,
               IvyXml.getAttrString(xml,"NAME"),
               IvyXml.getAttrString(xml,"CLONE"),xw);
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
         setExceptionBreakpoint(proj,IvyXml.getAttrBool(xml,"CAUGHT",false),
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
               IvyXml.getAttrString(xml,"FRAME"),
               IvyXml.getAttrEnum(xml,"ACTION",LspBaseDebugAction.NONE),xw);
         break;
      case "CONSOLEINPUT" :
         consoleInput(IvyXml.getAttrString(xml,"LAUNCH"),
               IvyXml.getTextElement(xml,"INPUT"));
         break;
      case "GETSTACKFRAMES" :
         getStackFrames(IvyXml.getAttrString(xml,"LAUNCH"),
               IvyXml.getAttrInt(xml,"COUNT",-1),
               IvyXml.getAttrInt(xml,"DEPTH",0),xw);
         break;
      case "VARVAL" :
         getVariableValue(IvyXml.getAttrString(xml,"FRAME"),
               IvyXml.getTextElement(xml,"VAR"),
               IvyXml.getAttrInt(xml,"DEPTH",1),xw);
         break;
      case "VARDETAIL" :
         getVariableValue(IvyXml.getAttrString(xml,"FRAME"),
               IvyXml.getTextElement(xml,"VAR"),-1,xw);
         break;
      case "EVALUATE" :
         evaluateExpression(proj,
               IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getTextElement(xml,"EXPR"),
               IvyXml.getAttrString(xml,"THREAD"),
               IvyXml.getAttrString(xml,"FRAME"),
               IvyXml.getAttrBool(xml,"IMPLICIT",false),
               IvyXml.getAttrBool(xml,"BREAK",true),
               IvyXml.getAttrString(xml,"REPLYID"),xw);
         break;
      default :
	 throw new LspBaseException("Unknown LSPBASE debug command " + cmd);   
    }
}



/********************************************************************************/
/*                                                                              */
/*      Setup debug protocol                                                    */
/*                                                                              */
/********************************************************************************/

LspBaseDebugProtocol getDebugProtocol(LspBaseProject proj)
{
   LspBaseLanguageData ld = proj.getLanguageData();
   LspBaseDebugProtocol ldp = debug_protocols.get(ld.getName());
   if (ldp == null) {
      synchronized (debug_protocols) {
         ldp = debug_protocols.get(ld.getName());
         if (ldp == null) {
            ldp = new LspBaseDebugProtocol(this,ld);
            debug_protocols.put(ld.getName(),ldp);
          }
       }
    }
   return ldp;
}


LspBaseDebugProtocol getDebugProtocol(LspBaseFile file)
{
   LspBaseLanguageData ld = file.getLanguageData();
   LspBaseDebugProtocol ldp = debug_protocols.get(ld.getName());
   if (ldp == null) {
      synchronized (debug_protocols) {
         ldp = debug_protocols.get(ld.getName());
         if (ldp == null) {
            ldp = new LspBaseDebugProtocol(this,ld);
            debug_protocols.put(ld.getName(),ldp);
          }
       }
    }
   return ldp;
}


/********************************************************************************/
/*										*/
/*	Run configuration methods						*/
/*										*/
/********************************************************************************/

void getNewRunConfiguration(String proj,String name,String clone,IvyXmlWriter xw)
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
      if (name == null) name = getUniqueName("Node Launch");
      plc = new LspBaseLaunchConfig(name);
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
   else if (prop.equals(LspBaseConfigAttribute.MAIN_TYPE)) {
      File f = new File(value);
      cfg.setFileToRun(f);
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




void getAllBreakpoints(IvyXmlWriter xw)
{
   for (LspBaseBreakpoint pb : break_map.values()) {
      pb.outputBubbles(xw);
    }
}



void setLineBreakpoint(String proj,String bid,String file,int line,boolean trace)
   throws LspBaseException
{
   LspBaseFile nf = lsp_base.getFileData(proj,file);
   for (LspBaseBreakpoint prev : break_map.values()) {
      if (prev.getType() == BreakType.LINE &&
	    prev.getFile().equals(nf.getFile()) &&
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


void setExceptionBreakpoint(String proj,
      boolean caught,boolean uncaught,boolean checked)
   throws LspBaseException
{
   LspBaseProject lbp = null;
   if (proj != null) {
      lbp = lsp_base.getProjectManager().findProject(proj);
    }
   
   if (exception_breakpoint == null) {
      exception_breakpoint = LspBaseBreakpoint.createExceptionBreakpoint(lbp,caught,uncaught);
      break_map.put(exception_breakpoint.getId(),exception_breakpoint);
      handleBreakNotify(exception_breakpoint,"ADD");
    }
   else {
      exception_breakpoint.setProperty("CAUGHT",Boolean.toString(caught));
      exception_breakpoint.setProperty("UNCAUGHT",Boolean.toString(uncaught));
      handleBreakNotify(exception_breakpoint,"CHANGE");
    }
   LspBaseBreakpoint pb = LspBaseBreakpoint.createExceptionBreakpoint(lbp,caught,uncaught);
   break_map.put(pb.getId(),pb);
   saveBreakpoints();
   handleBreakNotify(pb,"ADD");
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
         
         break;
      case FUNCTION :
         break;
      case NONE :
         break;
    }
   
   handleBreakNotify(bp,"CHANGE");
}




void clearLineBreakpoints(String proj,String file,int line)
{
   LspBaseFile nf = null;
   if (file != null) nf = lsp_base.getFileData(proj,file);
   
   boolean chng = false;
   for (Iterator<LspBaseBreakpoint> it = break_map.values().iterator(); it.hasNext(); ) {
      LspBaseBreakpoint bp = it.next();
      if (bp.getType() == BreakType.LINE) {
	 if (nf == null || bp.getFile().equals(nf)) {
	    if (line <= 0 || line <= bp.getLine()) {
	       it.remove();
	       handleBreakNotify(bp,"REMOVE");
	       chng = true;
	     }
	  }
       }
    }
   if (chng) {
      updateBreakpointsForFile(nf);
      saveBreakpoints();
    }
}



private void loadBreakpoints()
{
   Element xml = IvyXml.loadXmlFromFile(config_file);
   if (xml == null) {
      exception_breakpoint = LspBaseBreakpoint.createExceptionBreakpoint(null,
            false,true);
      break_map.put(exception_breakpoint.getId(),exception_breakpoint);
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
	    LspLog.logE("Breakpoint not found: " + IvyXml.convertXmlToString(xml),e);
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
      LspLog.logE("Problem writing out breakpoints",e);
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


private void updateBreakpointsForFile(LspBaseFile lbf)
{
   JSONObject src = createJson("path",lbf.getFile().getPath(),
      "presentationHint","normal");
   JSONArray bpts = new JSONArray();
   List<LspBaseBreakpoint> use = new ArrayList<>();
   for (LspBaseBreakpoint bp : break_map.values()) {
      if (bp.getFile() == lbf && bp.getType() == BreakType.LINE) {
         LineCol lc = bp.getLineColumn();
         JSONObject jbpt = createJson("line",lc.getLine(),"column",lc.getColumn());
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
   LspBaseDebugProtocol proto = getDebugProtocol(lbf);
   BreakpointsSet setter = new BreakpointsSet(use);
   proto.sendRequest("setBreakpoints",setter,
         "source",src,"breakpoints",bpts,"sourceModified",true);
}



private void updateExceptionBreakpoints(LspBaseProject proj)
{
   for (LspBaseDebugProtocol proto : debug_protocols.values()) {
      if (proj == null || proj.getLanguageData() == proto.getLanguage()) {
         updateExceptionBreakpoints(proto);
       }
    }
}


private void updateExceptionBreakpoints(LspBaseDebugProtocol proto) 
{
   JSONArray filters = new JSONArray();
   JSONArray filteropts = new JSONArray();
   JSONArray exceptopts = new JSONArray();
   
   List<LspBaseBreakpoint> use = new ArrayList<>();
   for (LspBaseBreakpoint bp : break_map.values()) {
      if (bp.getType() == BreakType.EXCEPTION) {
         use.add(bp);
         // add to filters, filteropts, exceptopts
       }
    }
   
   BreakpointsSet setter = new BreakpointsSet(use);
   proto.sendRequest("setExceptionBreakpoints",setter,
         "filters",filters,"filterOptions",filteropts,
         "exceptionOptions",exceptopts);
}



private class BreakpointsSet implements LspResponder {
   
   private List<LspBaseBreakpoint> break_points;
   
   BreakpointsSet(List<LspBaseBreakpoint> bpts) {
      break_points = bpts;
    }
   
   @Override public void handleResponse(Object resp,JSONObject err) {
      JSONObject data = (JSONObject) resp;
      JSONArray bpts = data.getJSONArray("breakpoints");
      for (int i = 0; i < break_points.size(); ++i) {
         JSONObject jdata = bpts.getJSONObject(i);
         LspBaseBreakpoint bp = break_points.get(i);
         LspLog.logD("SET BREAK DATA " + bp + " " + jdata.toString(2));
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
   target_map.put(tgt.getId(),tgt);
   tgt.startDebug();
   
   xw.begin("LAUNCH");
   xw.field("MODE","debug");
   xw.field("ID",tgt.getId());
   xw.field("CID",cfg.getId());
   xw.field("TARGET",tgt.getId());
   xw.field("PROCESS",tgt.getId());
   xw.end("LAUNCH");
}


void debugAction(String launchid,String targetid,String frameid,
      LspBaseDebugAction action,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseDebugTarget tgt = null;
   if (launchid != null) tgt = target_map.get(launchid);
   if (tgt == null && targetid != null) tgt = target_map.get(targetid);
   if (tgt == null) throw new LspBaseException("Target not found");
   
   boolean ok = tgt.debugAction(action);
   
   if (ok) xw.textElement("TARGET",tgt.getId());
}


void consoleInput(String launch,String input)
{
   
}




/********************************************************************************/
/*										*/
/*	Value methods								*/
/*										*/
/********************************************************************************/

void getStackFrames(String launchid,int count,int depth,IvyXmlWriter xw)
   throws LspBaseException
{
   xw.begin("STACKFRAMES");
   for (LspBaseDebugTarget tgt : target_map.values()) {
      if (launchid != null && !tgt.getId().equals(launchid)) continue;
      LspBaseDebugThread thrd = tgt.findThreadById(null);
      if (thrd != null) {
	 xw.begin("THREAD");
	 xw.field("NAME",thrd.getName());
	 xw.field("ID",thrd.getLocalId());
	 xw.field("TARGET",tgt.getId());
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
   xw.end("STACKFRAMES");
}


void getVariableValue(String frame,String var,int depth,IvyXmlWriter xw)
{
   
}


void evaluateExpression(String proj,String bid,String expr,String thread,
      String frame,boolean implicit,boolean stop,String eid,
      IvyXmlWriter xw)
   throws LspBaseException
{
   boolean done = false;
   int fidx = -1;
   if (frame != null) {
      fidx = Integer.parseInt(frame);
    }
   
   for (LspBaseDebugTarget tgt : target_map.values()) {
      LspBaseDebugThread thrd = tgt.findThreadById(thread);
      if (thrd != null) {
	 for (LspBaseDebugStackFrame frm : thrd.getStackFrames()) {
	    if (frm == null) continue;
	    if (fidx < 0 || fidx == frm.getIndex()) {
	       tgt.evaluateExpression(bid,eid,expr,frm.getIndex(),stop);
	       done = true;
	       break;
	     }
	  }
       }
      if (done) break;
    }
   
   if (!done) throw new LspBaseException("No evaluation to do");
}




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
      LspLog.logE("Problem writing out configurations",e);
    }
}



}       // end of class LspBaseDebugManager




/* end of LspBaseDebugManager.java */

