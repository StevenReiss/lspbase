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
   if (pfx == null) pfx = sym.optString("prefix",null);
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
   String det = sym.optString("detail",null);
   if (det != null) {
      LspBaseLanguageData ldata = project.getLanguageData();
      if (ldata.getCapabilityBool("useParameters")) {
         xw.field("PARAMETERS",det);
       }
      else {
         xw.field("DETAIL",det);
       }
      hdl += det;
    }
   xw.field("PREFIX",filpfx);
   xw.field("QNAME",qnam);
   xw.field("HANDLE",hdl);
   xw.end("ITEM");
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
   Object c = diagnostic.opt("code");
   if (c != null) {
      if (c instanceof Number) xw.field("MSGID",((Number) c).intValue());
      xw.field("MSGID",c.hashCode());
      xw.field("MSGCODE",c);
    }
   Object c1 = diagnostic.opt("codeDescription");
   if (c1 != null) xw.field("MSGDESCRITPION",c1);
   String c2 = diagnostic.optString("source",null);
   if (c2 != null) xw.field("MSGSOURCE",c2);
   Object c3 = diagnostic.opt("data");
   if (c3 != null) xw.field("MSGACTION",c3);
   int sev = diagnostic.optInt("severity",1);
   if (sev == 1) xw.field("ERROR",true);
   else if (sev == 2) xw.field("WARNING",true);
   else if (sev == 3) xw.field("INFO",true);
   else if (sev == 4) xw.field("HINT",true);
   
   outputProblemRange(file,diagnostic.getJSONObject("range"),xw);
  
   String msg = diagnostic.getString("message");
   msg = msg.replace(".\n","; ");
   msg = msg.replace("\n"," ");
   xw.cdataElement("MESSAGE",msg);
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
   xw.field("END",pos1);
}



/********************************************************************************/
/*                                                                              */
/*      Handle find results                                                     */
/*                                                                              */
/********************************************************************************/

static void outputFindResult(FindResult fr,IvyXmlWriter xw)
{
   xw.begin("MATCH");
   LspBaseFile lbf = fr.getFile();
   JSONObject range = fr.getRange();
   int soff = lbf.mapRangeToStartOffset(range);
   int eoff = lbf.mapRangeToEndOffset(range);
   xw.field("STARTOFFSET",soff);
   xw.field("LENGTH",eoff-soff);
   xw.field("FILE",lbf.getPath());
   JSONObject def = fr.getDefinition();
   if (def != null) {
      outputLspSymbol(lbf.getProject(),lbf,def,xw);
    }
   xw.end("MATCH");
}
   
   
}


// end of class LspBaseUtil




/* end of LspBaseUtil.java */

