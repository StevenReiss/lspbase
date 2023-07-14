/********************************************************************************/
/*                                                                              */
/*              LspBaseDebugVariable.java                                       */
/*                                                                              */
/*      Holder of a variable/value                                              */
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

import org.json.JSONObject;

import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseDebugVariable implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private JSONObject var_data;
private String var_name;
private int var_reference;
private LspBaseDebugThread for_thread;
private boolean is_local;
private boolean is_static;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseDebugVariable(JSONObject obj,LspBaseDebugScope scp) {
   var_data = obj;
   for_thread = scp.getThread();
   is_local = scp.isLocal();
   is_static = scp.isStatic();
}

LspBaseDebugVariable(String name,LspBaseDebugScope scp) {
   var_data = null;
   for_thread = scp.getThread();
   var_name = name;
   var_reference = scp.getReferenceNumber();
   is_local = false;
   is_static = scp.isStatic();
}


LspBaseDebugVariable(JSONObject var,LspBaseDebugThread thrd)
{
   var_data = var;
   for_thread = thrd;
   is_local = false;
   is_static = false;
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

int getReference()
{
   return var_reference;
}


LspBaseDebugThread getThread()                  { return for_thread; }




/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

void outputValue(IvyXmlWriter xw) 
{
   xw.begin("VALUE");
   if (var_data == null) {
      xw.field("HASVARS",true);
      xw.field("KIND","SCOPE");
      xw.field("NAME",var_name);
      xw.field("SAVEID",var_reference);
      xw.field("STATIC",true);
    }
   else {
      xw.field("NAME",var_data.getString("name"));
      String typ = var_data.optString("type",null);
      if (typ != null) xw.field("TYPE",typ);
      String ename = var_data.optString("evaluateName",null);
      if (ename != null) xw.field("EVAL",ename);
      int vref = var_data.getInt("variablesReference");
      if (vref > 0) xw.field("SAVEID",var_data.getInt("variablesReference"));
      int nvar = var_data.optInt("namedVariables",0);
      int nidx = var_data.optInt("indexedVariables",0);
      if (nvar+nidx > 0) xw.field("HASVARS",true);
      if (nidx > 0) xw.field("LENGTH",nidx);
      if (typ == null) {
         if (nidx > 0) xw.field("KIND","ARRAY");
         else if (nvar > 0 || vref > 0) xw.field("KIND","OBJECT");
         else xw.field("KIND","PRIMITIVE");
       }
      else if (for_thread.getTarget().isPrimitiveType(typ)) {
         xw.field("KIND","PRIMITIVE");
       }
      else if (for_thread.getTarget().isStringType(typ)) {
         xw.field("KIND","STRING");
       }
      else {
         xw.field("KIND","OBJECT");
       }
      xw.field("LOCAL",is_local);
      xw.field("STATIC",is_static);
      xw.cdataElement("DESCRIPTION",var_data.getString("value")); 
    }
   
   xw.end("VALUE");
}



}       // end of class LspBaseDebugVariable




/* end of LspBaseDebugVariable.java */

