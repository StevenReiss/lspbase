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

LspBaseDebugThread(LspBaseDebugTarget tgt,int id)
{ 
   debug_target = tgt;
   thread_id = id;
   thread_state = ThreadState.INIT;
   cur_breakpoints = null;
}

/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

List<LspBaseDebugStackFrame> getStackFrames()
{
   return new ArrayList<>();
}
     

String getName()                                { return null; }
int getId()                                     { return thread_id; }



/********************************************************************************/
/*                                                                              */
/*      Event handling                                                          */
/*                                                                              */
/********************************************************************************/

void setState(String state)                     
{ 
   switch (state) {
      case "started" :
         break;
      case "exited" :
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
   debug_target.postThreadEvent(this,"CHANGED",detail,text,false);
}
void handleContinued(JSONObject data)           
{ 
   thread_state = ThreadState.RUNNING;
   cur_breakpoints = null;
   debug_target.postThreadEvent(this,"CHANGED",null,null,false);
}
void handleTerminated()                        
{ 
   thread_state = ThreadState.TERMINATED;
   // possibly keep restart data here
   debug_target.postThreadEvent(this,"CHANGED",null,null,false);
}



/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

void outputXml(IvyXmlWriter xw)
{
   xw.begin("THREAD");
   xw.field("NAME",getName());
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

