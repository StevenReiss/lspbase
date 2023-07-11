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
private List<ScopeData> frame_scopes;
private List<VariableData> frame_variables;




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
      String path = sfobj.optString("path",null);
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
   
   // need to get variables
}




/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

int getIndex()                  { return frame_index; }
LspBaseFile getBaseFile()       { return base_file; }
int getId()                     { return frame_id; }



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
      ScopeData sd = new ScopeData(scp);
      frame_scopes.add(sd);
    }
}



void loadVariables(int depth)
{
   if (frame_scopes == null) return;
   
   LspBaseDebugProtocol proto = for_thread.getProtocol();
   
   frame_variables = new ArrayList<>();
   for (ScopeData sd : frame_scopes) {
      proto.sendRequest("variables",new VariableLoader(sd),
            ",Reference",sd.getReferenceNumber());
    }
}



private class VariableLoader implements LspResponder {
   
   private ScopeData for_scope;
   
   VariableLoader(ScopeData sd) {
      for_scope = sd;
    }
   
   @Override public void handleResponse(Object data,JSONObject err) {
      JSONObject body = (JSONObject) data;
      JSONArray vars = body.getJSONArray("variables");
      for (int i = 0; i < vars.length(); ++i) {
         JSONObject var = vars.getJSONObject(i);
         VariableData vd = new VariableData(var);
         frame_variables.add(vd);
       }
    
    }
}


/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

void outputXml(IvyXmlWriter xw,int ctr,int depth)
{
   xw.begin("STACKFRAME");
   xw.field("LEVEL",frame_index);
   xw.field("NAME",frame_method);
   xw.field("ID",frame_id);
   xw.field("LINENO",frame_line);
   xw.field("COLNO",frame_column);
   if (frame_file != null) xw.field("FILE",frame_file.getPath());
   else xw.field("SYSTEM",true);
   if (is_synthetic) xw.field("SYNTHETIC",true);
   // output variables
   
   xw.end("STACKFRAME");
}



/********************************************************************************/
/*                                                                              */
/*      Scope information                                                       */
/*                                                                              */
/********************************************************************************/

private class ScopeData {
   
   private JSONObject scope_data;
   
   ScopeData(JSONObject obj) {
      scope_data = obj;
    }
   
   int getReferenceNumber()             { return scope_data.getInt("variablesReference"); }
   
}       // end of inner class ScopeData



private class VariableData {
   
   private JSONObject var_data;
   
   VariableData(JSONObject obj) {
      var_data = obj;
    }
   
}       // end of inner class VariableData


}       // end of class LspBaseDebugStackFrame




/* end of LspBaseDebugStackFrame.java */

