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

import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

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
   String nm = sym.getString("name");
   String params = null;
   int idx = nm.indexOf("(");
   if (idx > 0) {
      params = nm.substring(idx);
      nm = nm.substring(0,idx);
    }
   xw.field("NAME",nm);
   if (params != null) xw.field("PARAMETERS",params);
   xw.field("TYPE",SymbolKinds[sym.getInt("kind")]);
   JSONObject range = loc.getJSONObject("range");
   
   outputRange(false,file,range,xw);
   JSONObject srange = loc.optJSONObject("selectionRange");
   if (srange != null) {
      outputRange(true,file,srange,xw);
    }
   
   String filpfx = project.getRelativeFile(file);
   String qnam = xpfx + nm;
   String hdl = project.getName() + ":" + filpfx + "#" + qnam;
   String det = sym.optString("detail",null);
   if (det != null) {
      LspBaseLanguageData ldata = project.getLanguageData();
      if (ldata.getCapabilityBool("lsp.useParameters")) {
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
   
   int fgs = 0;
   LspBaseLanguageData ld = file.getLanguageData();
   String ppfx = ld.getCapabilityString("lsp.privatePrefix");
   if (ppfx != null && nm.startsWith(ppfx)) {
      xw.field("FLAGS",Modifier.PRIVATE);
    }
   if (fgs != 0) xw.field("FLAGS",fgs);
   
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
   else if (sev == 3) {
      if (c != null && c.equals("TODO")) xw.field("TODO",true);
      else xw.field("NOTICE",true);
    }
   else if (sev >= 4) xw.field("HINT",true);
   
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
   

/********************************************************************************/
/*                                                                              */
/*      Output text edits                                                       */
/*                                                                              */
/********************************************************************************/

private static int edit_counter = 1;


static void outputTextEdit(LspBaseFile file,JSONArray editl,IvyXmlWriter xw)
{
   if (editl.length() > 1) {
      int minoff = -1;
      int maxoff = -1;
      Set<JSONObject> edits = new TreeSet<>(new EditComparator(file));
      for (int i = 0; i < editl.length(); ++i) {
         JSONObject ted = editl.getJSONObject(i);
         ted.put("index",i);
         JSONObject rng = ted.getJSONObject("range");
         edits.add(ted);
         int soff = file.mapRangeToStartOffset(rng);
         int eoff = file.mapRangeToEndOffset(rng);
         if (minoff < 0 || soff < minoff) minoff = soff;
         if (maxoff < 0 || eoff > maxoff) maxoff = eoff;
       }
      xw.begin("EDIT");
      xw.field("TYPE","COMPOSITE");
      xw.field("OFFSET",minoff);
      xw.field("LENGTH",maxoff-minoff);
      xw.field("ID",editl.hashCode());
      xw.field("COUNTER",++edit_counter);
      for (JSONObject ted : edits) {
         outputSingleEdit(file,ted,xw);
       }
      xw.end("EDIT");
    }
   else {
      JSONObject ted = editl.getJSONObject(0);
      outputSingleEdit(file,ted,xw);
    }
}



private static class EditComparator implements Comparator<JSONObject> {
   
   private LspBaseFile for_file;
   
   EditComparator(LspBaseFile f) {
      for_file = f;
    }
   
   @Override public int compare(JSONObject ed0,JSONObject ed1) {
      JSONObject rng0 = ed0.getJSONObject("range");
      JSONObject rng1 = ed1.getJSONObject("range");
      int soff0 = for_file.mapRangeToStartOffset(rng0);
      int eoff0 = for_file.mapRangeToEndOffset(rng0);
      int soff1 = for_file.mapRangeToStartOffset(rng1);
      int eoff1 = for_file.mapRangeToEndOffset(rng1);
      if (soff0 < soff1) return 1;
      else if (soff0 > soff1) return -1;
      else if (eoff0 < eoff1) return 1;
      else if (eoff0 > eoff1) return -1;
      
      return 0;
    }
}


private static void outputSingleEdit(LspBaseFile file,JSONObject ted,IvyXmlWriter xw)
{
   JSONObject rng = ted.getJSONObject("range");
   String ntxt = ted.getString("newText");
   int soff = file.mapRangeToStartOffset(rng);
   int eoff = file.mapRangeToEndOffset(rng);
   if (ntxt != null && ntxt.length() == 0) ntxt = null;
   xw.begin("EDIT");
   xw.field("OFFSET",soff);
   xw.field("LENGTH",eoff-soff);
   xw.field("ID",ted.hashCode());
   xw.field("COUNTER",++edit_counter);
   if (ntxt == null) {
      xw.field("TYPE","DELETE");
    }
   else if (eoff == soff) {
      xw.field("TYPE","INSERT");
      xw.cdataElement("TEXT",ntxt);
    }
   else {
      xw.field("TYPE","REPLACE");
      xw.cdataElement("TEXT",ntxt);
    }
   xw.end("EDIT");
}

   
}


// end of class LspBaseUtil




/* end of LspBaseUtil.java */

