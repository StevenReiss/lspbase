/********************************************************************************/
/*                                                                              */
/*              LspBaseDebugThread.java                                         */
/*                                                                              */
/*      Information for a debugger thread```                                       */
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseDebugThread implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

enum ThreadState { INIT, RUNNING, STOPPED, TERMINATED };

private int     thread_id;
private String  thread_name;
private ThreadState thread_state;
private LspBaseDebugTarget debug_target;
private List<LspBaseBreakpoint> cur_breakpoints;

private static Map<String,String> detail_map;

static {
   detail_map = new HashMap<>();
   detail_map.put("step","STEP_INTO");
   detail_map.put("breakpoint","BREAKPOINT");
   detail_map.put("exception","EXCEPTION");
   detail_map.put("pause","CLIENT_REQUEST");
   detail_map.put("entry","BREAKPOINT");
   detail_map.put("goto","CLIENT_REQUEST");
   detail_map.put("function breakpoint","BREAKPOINT");
   detail_map.put("data breakpoint","BREAKPOINT");
   detail_map.put("instruction breakpoint","BREAKPOINT");
}


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseDebugThread(LspBaseDebugTarget tgt,int id,boolean first)
{ 
   debug_target = tgt;
   thread_id = id;
   thread_state = ThreadState.INIT;
   cur_breakpoints = null;
   thread_name = "Thread_" + id;
   if (first) thread_name = "Main Thread";
         
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/


String getName()                                { return thread_name; }
int getId()                                     { return thread_id; }
LspBaseDebugTarget getTarget()                  { return debug_target; }

boolean isStopped()
{
   return thread_state == ThreadState.STOPPED;
}

LspBaseDebugProtocol getProtocol()
{
   return debug_target.getDebugProtocol();
}



/********************************************************************************/
/*                                                                              */
/*      Stack Frame methods                                                     */
/*                                                                              */
/********************************************************************************/

List<LspBaseDebugStackFrame> getStackFrames()
{
   LspBaseDebugProtocol proto = getProtocol();
   JSONObject format = createJson("hex",false,
         "parameters",true,"parameterTypes",true,
         "parameterValues",true,"line",true,
         "module",true,"includeAll",true);
   StackFramer sf = new StackFramer();
   proto.sendRequest("stackTrace",sf,
         "threadId",getId(),"startFrame",0,
         "levels",0,"format",format);
   
   // for each frame, do a scope command followed by variables
   for (LspBaseDebugStackFrame frm : sf.getFrames()) {
      if (frm.getBaseFile() != null) {
         proto.sendRequest("scopes",new FrameScoper(frm),
               "frameId",frm.getId());
         frm.loadVariables(1);
       }
    }
 
   return sf.getFrames();
}



private class FrameScoper implements LspResponder {
   
   private LspBaseDebugStackFrame for_frame;
   
   FrameScoper(LspBaseDebugStackFrame frm) {
      for_frame = frm;
    }
   
   @Override public void handleResponse(Object data,JSONObject err) {
      JSONObject body = (JSONObject) data;
      JSONArray scps = body.getJSONArray("scopes");
      for_frame.addScopes(scps);
    }
}


private class StackFramer implements LspResponder {
   
   private List<LspBaseDebugStackFrame> cur_frames;
   
   StackFramer() {
      cur_frames = new ArrayList<>();
    }
   
   List<LspBaseDebugStackFrame> getFrames()             { return cur_frames; }
   
   @Override public void handleResponse(Object data,JSONObject err) {
      if (data instanceof JSONObject) {
         JSONObject body = (JSONObject) data;
         JSONArray frames = body.getJSONArray("stackFrames");
         for (int i = 0; i < frames.length(); ++i) {
            JSONObject frame = frames.getJSONObject(i);
            LspBaseDebugStackFrame frm = new LspBaseDebugStackFrame(LspBaseDebugThread.this,i,frame);
            cur_frames.add(frm);
          }
       }
    }
}



/********************************************************************************/
/*                                                                              */
/*      Command handling                                                        */
/*                                                                              */
/********************************************************************************/

boolean debugAction(LspBaseDebugAction action,String frameid)
{
   LspBaseDebugProtocol proto = debug_target.getDebugProtocol();
   ActionStatus sts = new ActionStatus();
   switch (action) {
      case NONE :
         break;
      case SUSPEND :
         switch (thread_state) {
            case RUNNING :
               proto.sendRequest("pause",sts,
                     "threadId",getId());
               break;
          }
         break;
      case RESUME :
         switch (thread_state) {
            case STOPPED :
               proto.sendRequest("continue",sts,
                     "threadId",getId(),"singleThread",true);
               break;
          }
         break;
      case DROP_TO_FRAME :
         switch (thread_state) {
            case STOPPED :
               proto.sendRequest("restartFrame",sts,
                     "threadId",getId(),"singleThread",true,
                     "frameId",Integer.parseInt("frameid"),
                     "granularity","statement");
               break;
          }
         break;
      case STEP_INTO :
         switch (thread_state) {
            case STOPPED :
               proto.sendRequest("stepIn",sts,
                     "threadId",getId(),"singleThread",true,
                     "granularity","statement");
               break;
          }
         break;
      case STEP_OVER :
         switch (thread_state) {
            case STOPPED :
               proto.sendRequest("next",sts,
                     "threadId",getId(),"singleThread",true,
                     "granularity","statement");
               break;
          }
         break;
      case STEP_RETURN :
         switch (thread_state) {
            case STOPPED :
               proto.sendRequest("stepOut",sts,
                     "threadId",getId(),"singleThread",true,
                     "granularity","statement");
               break;
          }
         break;
      case TERMINATE :
         switch (thread_state) {
            case INIT :
            case STOPPED :
            case RUNNING :
               JSONArray tids = new JSONArray();
               tids.put(getId());
               proto.sendRequest("terminateThreads",sts,
                     "threadIds",tids);
               break;
          }
         break;
    }
   
   return sts.isOkay();
}



/********************************************************************************/
/*                                                                              */
/*      Handle Evaluations                                                      */
/*                                                                              */
/********************************************************************************/

void getVariableValue(String fid,String var,int saveid,int depth,int arr,
      boolean detail,IvyXmlWriter xw)
{
   LspBaseDebugProtocol proto = debug_target.getDebugProtocol();
   
   xw.begin("VALUE");
   if (detail) {
      // only want DESCRIPTION FIELD
    }
   else {
      VariableLoader vl = new VariableLoader();
      proto.sendRequest("variables",vl,
            "variableReference",saveid);
      
      JSONArray vars = vl.getVariables();
      
      for (int i = 0; i < vars.length(); ++i) {
         JSONObject va = vars.getJSONObject(i);
         LspBaseDebugVariable vd = new LspBaseDebugVariable(va,this);
         vd.outputValue(xw);
       }
    }
   xw.end("VALUE");
   
   // here we should use the code for Variable Data with the current
   // value being the stack frame
   // xw.begin("VALUE");
   // output values
   // xw.end("VALUE");
   
}


private class VariableLoader implements LspResponder {

   private JSONArray variable_set;
   
   VariableLoader() {
      variable_set = null;
    }
   
   JSONArray getVariables()             { return variable_set; }
   
   @Override public void handleResponse(Object data,JSONObject err) {
      JSONObject body = (JSONObject) data;
      variable_set = body.getJSONArray("variables");
    }
   
}       // end of inner class VariableLoader




private class ActionStatus implements LspResponder {
   
   private boolean is_error;
   
   ActionStatus() {
      is_error = true;
    }
   
   boolean isOkay()                             { return !is_error; }
   
   @Override public void handleResponse(Object data,JSONObject err) {
      if (err != null) is_error = true;
      is_error = false;
    }
   
}       // end of inner class ActionStatus



/********************************************************************************/
/*                                                                              */
/*      Event handling                                                          */
/*                                                                              */
/********************************************************************************/

void setState(String state)                     
{ 
   switch (state) {
      case "started" :
         thread_state = ThreadState.RUNNING;
         break;
      case "exited" :
         thread_state = ThreadState.TERMINATED;
         break;
      default :
         break;
    }
}
void handleStopped(JSONObject data)             
{
   thread_state = ThreadState.STOPPED;
   String reason = data.getString("reason");
   String detail = detail_map.get(reason);
   String text = data.optString("text",null);
   JSONArray arr = data.optJSONArray("hitBreakpointIds");
   if (arr != null) {
      cur_breakpoints = new ArrayList<>();
      for (int i = 0; i < arr.length(); ++i) {
         int id = arr.getInt(i);
         LspBaseBreakpoint bpt = debug_target.findBreakpoint(id);
         if (bpt != null) cur_breakpoints.add(bpt);
       }
    }
   debug_target.postThreadEvent(this,"CHANGE",detail,text,false);
}
void handleContinued(JSONObject data)           
{ 
   thread_state = ThreadState.RUNNING;
   cur_breakpoints = null;
   debug_target.postThreadEvent(this,"CHANGE",null,null,false);
}
void handleTerminated()                        
{ 
   thread_state = ThreadState.TERMINATED;
   // possibly keep restart data here
   debug_target.postThreadEvent(this,"CHANGE",null,null,false);
}



/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

void outputXml(IvyXmlWriter xw)
{
   xw.begin("THREAD");
   String nm = getName();
   if (nm != null) xw.field("NAME",nm);
   else {
      
    }
   xw.field("ID",getId());
   xw.field("STACK",thread_state == ThreadState.STOPPED);
   // check for system thread
   // check for daemon thread
   xw.field("TERMINATED",thread_state == ThreadState.TERMINATED);
   xw.field("SUSPENDED",thread_state == ThreadState.STOPPED);
   xw.field("PID", debug_target.getId());
   debug_target.outputLaunch(xw); 
   if (cur_breakpoints != null) {
      for (LspBaseBreakpoint bpt : cur_breakpoints) {
         bpt.outputXml(xw);
       }
    }
   xw.end("THREAD");
}



}       // end of class LspBaseDebugThread




/* end of LspBaseDebugThread.java */

