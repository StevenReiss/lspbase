/********************************************************************************/
/*                                                                              */
/*              LspBaseBreakpoint.java                                          */
/*                                                                              */
/*      Breakpoint information                                                  */
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


import javax.swing.text.Position;
import javax.swing.text.Segment;

import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

abstract class LspBaseBreakpoint implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private boolean 	is_enabled;
private String		break_id;
private int		break_number;
private String 	trace_log;
private String		debug_condition;
private String          hit_condition;
private String          log_message;
private boolean 	condition_enabled;
private int             external_id;
private boolean         is_verified;


private static IdCounter break_counter = new IdCounter();


/********************************************************************************/
/*										*/
/*	Static creation methods 						*/
/*										*/
/********************************************************************************/

static LspBaseBreakpoint createLineBreakpoint(LspBaseFile file,int line)
   throws LspBaseException
{
   return new LineBreakpoint(file,line);
}



static LspBaseBreakpoint createExceptionBreakpoint(LspBaseProject bp,String name,boolean caught,boolean uncaught)
{
   return new ExceptionBreakpoint(bp,name,caught,uncaught);
}


static LspBaseBreakpoint createFunctionBreakpoint(LspBaseFile file,String function)
{ 
   return null;
}


static LspBaseBreakpoint createBreakpoint(Element xml) throws LspBaseException
{
   BreakType bt = IvyXml.getAttrEnum(xml,"TYPE",BreakType.NONE);
   switch (bt) {
      case NONE :
	 break;
      case LINE :
	 return new LineBreakpoint(xml);
      case EXCEPTION :
	 return new ExceptionBreakpoint(xml);
      case FUNCTION :
         return null;
      case DATA :
         return null;
    }
   
   return null;
}


static LspBaseBreakpoint createBreakpoint(JSONObject jbpt)
{
   JSONObject src = jbpt.optJSONObject("source");
   if (src == null) return null;
   String path = src.optString("path");
   if (path == null) return null;
   LspBaseFile file = LspBaseMain.getLspMain().getProjectManager().findFile(null,path);
   int line = src.optInt("line",-1);
   if (line < 0) return null;
   LspBaseBreakpoint bpt = null;
   try {
      bpt = new LineBreakpoint(file,line);
      bpt.setProtoInfo(jbpt);
    }
   catch (LspBaseException e) {
      return null;
    }
   return bpt;
}



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

protected LspBaseBreakpoint()
{
   break_number = break_counter.nextValue();
   break_id = "BREAK_" + Integer.toString(break_number);
   is_enabled = true;
   trace_log = null;
   debug_condition = null;
   hit_condition = null;
   log_message = null;
   condition_enabled = false;
   external_id = -1;
   is_verified = false;
}



protected LspBaseBreakpoint(Element xml)
{
   break_number = IvyXml.getAttrInt(xml,"ID");
   break_counter.noteValue(break_number);
   break_id = "BREAK_" + Integer.toString(break_number);
   break_id = IvyXml.getAttrString(xml,"ID");
   
   is_enabled = IvyXml.getAttrBool(xml,"ENABLED");
   trace_log = IvyXml.getAttrString(xml,"TRACEPOINT");
   debug_condition = IvyXml.getTextElement(xml,"CONDITION");
   hit_condition = IvyXml.getTextElement(xml,"HITCONDITION");
   log_message = IvyXml.getTextElement(xml,"LOGMESSAGE");
   condition_enabled = IvyXml.getAttrBool(xml,"CONDENABLED");
}




/********************************************************************************/
/*										*/
/*	Access methods								*/
/*										*/
/********************************************************************************/

String getId()				{ return break_id; }

boolean isEnabled()			{ return is_enabled; }
String getCondition()			{ return debug_condition; }
String getHitCondition()                { return hit_condition; }
boolean isConditionEnabled()		{ return condition_enabled; }
String getTraceLog()                    { return trace_log; }
int getExternalId()                     { return external_id; }
boolean isVerified()                    { return is_verified; }


void setConditionEnabled(boolean e)	{ condition_enabled = e; }
void setCondition(String c)
{
   if (c != null && c.trim().length() == 0) c = null;
   debug_condition = c;
}

void setHitCondition(String c) 
{
   if (c != null && c.trim().length() == 0) c = null;
   hit_condition = c;
}


void setProperty(String p,String v)
{
   if (p == null) return;
   if (p.equals("ENABLE") || p.equals("ENABLED")) {
      if (v == null) is_enabled = true;
      else is_enabled = Boolean.parseBoolean(v);
    }
   else if (p.equals("DISABLE") || p.equals("DISABLED")) {
      is_enabled = false;
    }
}

abstract BreakType getType();

LspBaseFile getFile()			{ return null; }
int getLine()				{ return -1; }
LineCol getLineColumn()                 { return null; }
LspBaseProject getProject()       
{
   return getFile().getProject();
}

String getException()			{ return null; }
boolean isCaught()			{ return false; }
boolean isUncaught()			{ return false; }

void clear()
{ }
 
void setProtoInfo(JSONObject data) 
{
   is_verified = data.getBoolean("verified");
   external_id = data.optInt("id",-1);
   trace_log = data.optString("message",trace_log);
   // TODO: check if we need to look at line,column,endline,endcolumn,source
}



/********************************************************************************/
/*										*/
/*	Output Methods								*/
/*										*/
/********************************************************************************/

void outputXml(IvyXmlWriter xw)
{
   xw.begin("BREAKPOINT");
   xw.field("TYPE",getType());
   xw.field("ID",break_number);
   xw.field("ENABLED",is_enabled);
   xw.field("CONDENABLED",condition_enabled);
   if (trace_log != null) xw.field("TRACEPOINT",trace_log);
   
   outputLocalXml(xw);
   
   if (debug_condition != null) xw.cdataElement("CONDITION",debug_condition);
   if (hit_condition != null) xw.cdataElement("HITCONDITION",hit_condition);
   if (log_message != null) xw.cdataElement("LOGMESSAGE",log_message);
   
   xw.end("BREAKPOINT");
}

protected abstract void outputLocalXml(IvyXmlWriter xw);



void outputBubbles(IvyXmlWriter xw)
{
   xw.begin("BREAKPOINT");
   xw.field("TYPE",getType());
   xw.field("ENABLED",is_enabled);
   xw.field("ID",break_id);
   xw.field("SUSPEND", "VM");
   xw.field("HITCOUNT",0);
   xw.field("TRACEPOINT",(trace_log != null));
   xw.field("TRACELOG",trace_log);
   
   outputLocalBubbles(xw);
   
   if (debug_condition != null) {
      xw.begin("CONDITION");
      xw.field("ENABLED",condition_enabled);
      xw.text(debug_condition);
      xw.end("CONDITION");
    }
   
   if (debug_condition != null) {
      xw.cdataElement("HITCONDITION",hit_condition);
    }
   
   if (log_message != null) {
      xw.cdataElement("LOGMESSAGE",log_message);
    }
   
   xw.end("BREAKPOINT");
}


protected abstract void outputLocalBubbles(IvyXmlWriter xw);




/********************************************************************************/
/*										*/
/*	Line breakpoint specifics						*/
/*										*/
/********************************************************************************/

private static class LineBreakpoint extends LspBaseBreakpoint
{
   private LspBaseFile	   file_data;
   private Position	   file_position;
   
   LineBreakpoint(LspBaseFile file,int line) throws LspBaseException {
      file_data = file;
      int off = file.mapLineToOffset(line);
      file_position = file.createPosition(off);
    }
   
   LineBreakpoint(Element xml) throws LspBaseException {
      super(xml);
      LspBaseMain pm = LspBaseMain.getLspMain();
      String fnm = IvyXml.getTextElement(xml,"FILE");
      String pnm = IvyXml.getAttrString(xml,"PROJECT");
      LspBaseProject bp = pm.getProjectManager().findProject(pnm);
      bp.open();
      file_data = bp.findFile(fnm);
      if (file_data == null) throw new LspBaseException("File " + fnm + " not found");
      int line = IvyXml.getAttrInt(xml,"LINE");
      int off = file_data.mapLineToOffset(line);
      int off1 = file_data.mapLineToOffset(line+1);
      Segment s = file_data.getSegment(off,off1-off);
      for (int i = 0; i < s.length(); ++i) {
         char c = s.charAt(i);
         if (Character.isWhitespace(c)) ++off;
         else break;
       }
      
      file_position = file_data.createPosition(off);
    }
   
   @Override BreakType getType()			{ return BreakType.LINE; }
   
   @Override LspBaseFile getFile()		      	{ return file_data; }
   
   @Override int getLine() {
      int off = file_position.getOffset();
      return file_data.mapOffsetToLine(off);
    }
   
   @Override LineCol getLineColumn() {
      return file_data.mapOffsetToLineColumn(file_position.getOffset());
    }
   
   @Override void clear() {
      file_data = null;
      file_position = null;
    }
   
   @Override protected void outputLocalXml(IvyXmlWriter xw) {
      xw.field("PROJECT",file_data.getProject().getName());
      xw.field("FILE",file_data.getPath());
      LineCol lc = getLineColumn();
      xw.field("LINE",lc.getLine());
      xw.field("COLUMN",lc.getColumn());
      xw.field("OFFSET",file_position.getOffset());
    }
   
   @Override protected void outputLocalBubbles(IvyXmlWriter xw) {
      xw.field("PROJECT",file_data.getProject().getName()); 
      LineCol lc = getLineColumn();
      xw.field("LINE",lc.getLine());
      xw.field("COLUMN",lc.getColumn());
      xw.field("FILE",file_data.getPath());
      xw.field("STARTPOS",file_position.getOffset());
      xw.field("ENDPOS",file_position.getOffset());
    }
   
}	// end of inner class LineBreakpoint



/********************************************************************************/
/*										*/
/*	Exception breakpoint specifics						*/
/*										*/
/********************************************************************************/

private static class ExceptionBreakpoint extends LspBaseBreakpoint
{
   private boolean	is_caught;
   private boolean	is_uncaught;
   private LspBaseProject for_project;
   private String       exception_name;
   
   ExceptionBreakpoint(LspBaseProject bp,String name,boolean c,boolean u) {
      is_caught = c;
      is_uncaught = u;
      for_project = bp;
      exception_name = name;
    }
   
   ExceptionBreakpoint(Element xml) {
      super(xml);
      is_caught = IvyXml.getAttrBool(xml,"ISCAUGHT");
      is_uncaught = IvyXml.getAttrBool(xml,"ISUNCAUGHT");
      String pnm = IvyXml.getAttrString(xml,"PROJECT");
      exception_name = IvyXml.getAttrString(xml,"NAME");
      if (exception_name != null && exception_name.isEmpty()) exception_name = null;
      
      for_project = null;
      LspBaseMain lsp = LspBaseMain.getLspMain();
      LspBaseProjectManager pm = lsp.getProjectManager();
      try {
         if (pnm != null) for_project = pm.findProject(pnm);
       }
      catch (LspBaseException e) { }
    }
   
   @Override BreakType getType()			{ return BreakType.EXCEPTION; }
   @Override public boolean isCaught()		        { return is_caught; }
   @Override public boolean isUncaught()		{ return is_uncaught; }
   @Override LspBaseProject getProject()                { return for_project; }
   @Override public String getException()               { return exception_name; }
   
   @Override protected void outputLocalXml(IvyXmlWriter xw) {
      xw.field("ISCAUGHT",is_caught);
      xw.field("ISUNCAUGHT",is_uncaught);
      xw.field("PROJECT",for_project.getName());
      if (exception_name != null) xw.field("NAME",exception_name);
    }
   
   @Override protected void outputLocalBubbles(IvyXmlWriter xw) {
      xw.field("ISCAUGHT",is_caught);
      xw.field("ISUNCAUGHT",is_uncaught);
    }
   
   @Override void setProperty(String p,String v) {
      if (p == null) return;
      if (p.equals("CAUGHT")) is_caught = Boolean.parseBoolean(v);
      else if (p.equals("UNCAUGHT")) is_uncaught = Boolean.parseBoolean(v);
      else if (p.equals("NAME") || p.equals("CLASS")) {
         exception_name = v;
       }
      else super.setProperty(p,v);
    }
   
}	// end of inner class LineBreakpoint




}       // end of class LspBaseBreakpoint




/* end of LspBaseBreakpoint.java */

