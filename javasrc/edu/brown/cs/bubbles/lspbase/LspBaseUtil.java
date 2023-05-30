/********************************************************************************/
/*                                                                              */
/*              LspBaseUtil.java                                                */
/*                                                                              */
/*      Utility methods for LSP -- mainly output                                */
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

import org.json.JSONArray;
import org.json.JSONObject;

import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseUtil implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

static final String [] SymbolKinds = {
   "None", "File", "Module", "Namespace", "Package",
   "Class", "Method", "Property", "Field", "Constructor",
   "Enum", "Interface", "Function", "Variable", "Constant",
   "String", "Number", "Boolean", "Array", "Object",
   "Key", "Null", "EnumMember", "Struct", "Event",
   "Operator", "TypeParameter",
};



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/


/********************************************************************************/
/*                                                                              */
/*      Symbol output                                                           */
/*                                                                              */
/********************************************************************************/


static void outputLspSymbol(LspBaseProject project,
      LspBaseFile file,JSONObject sym,IvyXmlWriter xw)
{
   outputLspSymbol(project,file,sym,null,xw);
}
 

static void outputLspSymbol(LspBaseProject project,
      LspBaseFile file,JSONObject sym,String pfx,IvyXmlWriter xw)
{
    outputSymbol(project,file,sym,pfx,xw);
    JSONArray arr = sym.optJSONArray("children");
    if (arr != null) {
       if (pfx == null) pfx = sym.getString("name");
       else pfx = pfx + "." + sym.getString("name");
       for (int i = 0; i < arr.length(); ++i) {
          outputLspSymbol(project,file,arr.getJSONObject(i),pfx,xw);
        }
     }
}



private static void outputSymbol(LspBaseProject project,LspBaseFile file,JSONObject sym,String pfx,IvyXmlWriter xw)
{
   String xpfx = (pfx == null ? "" : pfx + ".");
   JSONObject loc = sym.optJSONObject("location");
   if (loc != null) {
      String fileuri = loc.getString("uri");
      LspBaseFile nfil = project.findFile(fileuri);
      if (nfil != null) file = nfil;
    }
   else { 
      loc = sym;
    }
   
   xw.begin("ITEM");
   xw.field("PROJECT",project.getName());
   xw.field("PATH",file.getPath());
   xw.field("NAME",sym.getString("name"));
   xw.field("TYPE",SymbolKinds[sym.getInt("kind")]);
   JSONObject range = loc.getJSONObject("range");
   
   outputRange(false,file,range,xw);
   JSONObject srange = loc.optJSONObject("selectionRange");
   if (srange != null) {
      outputRange(true,file,srange,xw);
    }
   
   String filpfx = project.getRelativeFile(file);
   String qnam = xpfx + sym.getString("name");
   String hdl = project.getName() + ":" + filpfx + "#" + qnam;
   String det = sym.optString("detail");
   if (det != null) {
      String xdet = cleanDetail(det);
      // remove variable names from detail
      hdl += xdet;
    }
   xw.field("PREFIX",filpfx);
   xw.field("QNAME",qnam);
   xw.field("HANDLE",hdl);
   xw.end("ITEM");
}



private static String cleanDetail(String det)
{
   StringBuffer buf = new StringBuffer();
   int state = 0;
   int lvl = 0;
   StringBuffer typ = null;
   for (int i = 0; i < det.length(); ++i) {
      char c = det.charAt(i);
      switch (state) {
         case 0 :
            // add prefix
            buf.append(c);
            if (c == '(') {
               state = 1;
             }
            break;
         case 1 :
            // scannning type/parameter name
            if (typ == null) typ = new StringBuffer();
            if (c == '<') ++lvl;
            else if (lvl > 0) {
               if (c == '>') --lvl;
             }
            else if (c == ' ') {
               if (i+1 < det.length() && det.charAt(i) != ',') {
                  buf.append(typ);
                  typ = null;
                }
               state = 2;
               break;
             }
            else if (c == ',' || c == ')') {
               // parameter name only, no type
               buf.append("dynamic");
               buf.append(c);
               typ = null;
               if (c == ',') state = 1;
               else state = 3;
               break;
             }
            typ.append(c);
            break;
         case 2 :
            // skip variable name
            if (c == ',' || c == ')') {
               state = 3;
               buf.append(c);
             }
            break;
         case 3 :
            // add remainder
            buf.append(c);
            break;
       }
    }
   
   return buf.toString();
}



private static void outputRange(boolean extended,LspBaseFile file,JSONObject range,IvyXmlWriter xw)
{
   JSONObject start = range.getJSONObject("start");
   JSONObject end = range.getJSONObject("end");
   int ln0 = start.getInt("line") + 1;
   int ch0 = start.getInt("character") + 1;
   int pos0 = file.mapLineCharToOffset(ln0,ch0);
   int ln1 = end.getInt("line") + 1;
   int ch1 = end.getInt("character") + 1;
   int pos1 = file.mapLineCharToOffset(ln1,ch1);
   if (!extended) {
      xw.field("LINE",ln0);
      xw.field("COL", ch0);
      xw.field("STARTOFFSET",pos0);
      xw.field("ENDOFFSET",pos1-1);
      xw.field("LENGTH",pos1-pos0);
    }
   else {
      xw.field("NAMEOFFSET",pos0);
      xw.field("NAMELENGTH",pos1-pos0);
    }
}



/********************************************************************************/
/*                                                                              */
/*      Output Diagnostic messages                                              */
/*                                                                              */
/********************************************************************************/

static void outputDiagnostic(LspBaseFile file,JSONObject diagnostic,IvyXmlWriter xw)
{
   xw.begin("PROBLEM");
   xw.field("PROJECT",file.getProject().getName());
   xw.field("FILE",file.getPath());
   xw.field("MESSAGE",diagnostic.getString("message"));
   Object c = diagnostic.opt("code");
   if (c != null) xw.field("MSGID",c);
   Object c1 = diagnostic.opt("codeDescription");
   if (c1 != null) xw.field("MSGDESCRITPION",c1);
   String c2 = diagnostic.optString("source");
   if (c2 != null) xw.field("MSGSOURCE",c2);
   Object c3 = diagnostic.opt("data");
   if (c3 != null) xw.field("MSGACTION",c3);
   
   outputProblemRange(file,diagnostic.getJSONObject("range"),xw);
   
   JSONArray info = diagnostic.optJSONArray("relatedInformation");
   if (info != null) {
      for (int i = 0; i < info.length(); ++i) {
         xw.textElement("ARG",info.get(i).toString());
       }
    }
   xw.end("PROBLEM");
}




private static void outputProblemRange(LspBaseFile file,JSONObject range,IvyXmlWriter xw)
{
   JSONObject start = range.getJSONObject("start");
   JSONObject end = range.getJSONObject("end");
   int ln0 = start.getInt("line") + 1;
   int ch0 = start.getInt("character") + 1;
   int pos0 = file.mapLineCharToOffset(ln0,ch0);
   int ln1 = end.getInt("line") + 1;
   int ch1 = end.getInt("character") + 1;
   int pos1 = file.mapLineCharToOffset(ln1,ch1);
   xw.field("LINE",ln0);
   xw.field("COL", ch0);
   xw.field("START",pos0);
   xw.field("END",pos1-1);
}

}       // end of class LspBaseUtil




/* end of LspBaseUtil.java */

