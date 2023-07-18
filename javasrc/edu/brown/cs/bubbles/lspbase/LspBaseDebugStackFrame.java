/********************************************************************************/
/*                                                                              */
/*              LspBaseDebugStackFrame.java                                     */
/*                                                                              */
/*      Representation of a stack frame                                         */
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
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseDebugStackFrame implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private LspBaseDebugThread for_thread;
private int     frame_index;
private int     frame_id;
private String  frame_method;
private File    frame_file;
private LspBaseFile base_file;
private int     frame_line;
private int     frame_column;
private boolean is_synthetic;
private List<LspBaseDebugScope> frame_scopes;
private List<LspBaseDebugVariable> frame_variables;




/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseDebugStackFrame(LspBaseDebugThread thrd,int idx,JSONObject sfobj)
{
   for_thread = thrd;
   frame_index = idx;
   frame_id = sfobj.getInt("id");
   frame_method = sfobj.getString("name");
   frame_file = null;
   base_file = null;
   JSONObject src = sfobj.optJSONObject("source");
   if (src != null) {
      String path = src.optString("path",null);
      if (path != null) {
         frame_file = new File(path);
         base_file = LspBaseMain.getLspMain().getProjectManager().findFile(null,path);
       }
   }
   frame_line = sfobj.getInt("line");
   frame_column = sfobj.getInt("column");
   frame_scopes = null;
   frame_variables = null;
   is_synthetic = false;
   String hint = sfobj.optString("presentationHint","normal");
   if (hint.equals("label")) is_synthetic = true;
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

int getIndex()                  { return frame_index; }

LspBaseDebugThread getThread()  { return for_thread; }
LspBaseFile getBaseFile()       { return base_file; }
int getId()                     { return frame_id; }



/********************************************************************************/
/*                                                                              */
/*      Evaluation methods                                                      */
/*                                                                              */
/********************************************************************************/

LspBaseDebugVariable evaluateExpression(String expr) 
      throws LspBaseException
{ 
   LspBaseDebugProtocol proto = for_thread.getProtocol();
   EvalHandler eh = new EvalHandler();
   
   proto.sendRequest("evaluate",eh,
         "expression",expr,"frameId",getId(),
         "context","varaiables");
   
   JSONObject rslt = eh.getResult();
   LspLog.logD("Evaluation result " + rslt);
   LspBaseDebugVariable var = new LspBaseDebugVariable(rslt,getThread());
   
   return var;
}



private class EvalHandler implements LspJsonResponder {
   
   private JSONObject eval_result;
   
   EvalHandler() {
      eval_result = null;
    }
   
   JSONObject getResult()               { return eval_result; }
   
   @Override public void handleResponse(JSONObject resp) {
      eval_result = resp;
    }
}



/********************************************************************************/
/*                                                                              */
/*      Variable methods                                                        */
/*                                                                              */
/********************************************************************************/

void addScopes(JSONArray scopes)
{
   frame_scopes = new ArrayList<>();
   for (int i = 0; i < scopes.length(); ++i) {
      JSONObject scp = scopes.getJSONObject(i);
      LspBaseDebugScope sd = new LspBaseDebugScope(this,scp);
      frame_scopes.add(sd);
    }
}



void loadVariables(int depth)
{
   if (frame_scopes == null) return;
   
   frame_variables = new ArrayList<>();
   
   LspBaseDebugProtocol proto = for_thread.getProtocol();
   
   for (LspBaseDebugScope sd : frame_scopes) {
      String dnm = sd.getDelayName();
      if (dnm != null) {
         LspBaseDebugVariable vd = new LspBaseDebugVariable(dnm,sd);
         frame_variables.add(vd);
       }
      else {
         try {
            proto.sendRequest("variables",new VariableLoader(sd),
                  "variablesReference",sd.getReferenceNumber());
          }
         catch (LspBaseException e) {
            LspLog.logE("DEBUG problem loading variables",e);
          }
       }
    }
}



private class VariableLoader implements LspJsonResponder {
   
   private LspBaseDebugScope scope_data;
   
   VariableLoader(LspBaseDebugScope sd) { 
      scope_data = sd;
    }
   
   @Override public void handleResponse(JSONObject body) {
      JSONArray vars = body.getJSONArray("variables");
      for (int i = 0; i < vars.length(); ++i) {
         JSONObject var = vars.getJSONObject(i);
         LspBaseDebugVariable vd = new LspBaseDebugVariable(var,scope_data);
         frame_variables.add(vd);
       }
    
    }
   
}       // end of inner class VariableLoader


/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

void outputXml(IvyXmlWriter xw,int ctr,int depth)
{
   xw.begin("STACKFRAME");
   xw.field("LEVEL",frame_index);
   xw.field("METHOD",frame_method);
   xw.field("ID",frame_id);
   xw.field("LINENO",frame_line);
   xw.field("COLNO",frame_column);
   if (frame_file != null) xw.field("FILE",frame_file.getPath());
   if (base_file != null) xw.field("FILETYPE","SOURCEFILE");
   else xw.field("FILETYPE","CLASSFILE");
   if (is_synthetic) xw.field("SYNTHETIC",true);
  
   if (frame_variables != null) {
      for (LspBaseDebugVariable bd : frame_variables) {
         bd.outputValue(xw);
       }
    }
   
   xw.end("STACKFRAME");
}



}       // end of class LspBaseDebugStackFrame




/* end of LspBaseDebugStackFrame.java */

