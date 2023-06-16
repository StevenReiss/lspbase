/********************************************************************************/
/*                                                                              */
/*              LspBaseElider.java                                              */
/*                                                                              */
/*      Handle elisions for a file                                              */
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseElider implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private Set<ElideRegion> elide_rdata;
private LspBaseFile for_file;

private String [] token_types;
private String [] token_modifiers;


private static double DOWN_PRIORITY = 0.90;
private static double CHILD_PRIORITY = 0.98;

private String [] ElideDeclTypes = {
      null, "COMPUNIT", "MODULE", null, null,
      "CLASS", "METHOD", "ANNOT", "FIELD", "METHOD",
      "ENUM", "CLASS", "METHOD", "VARDECL", "VARDECL",
      null, null, null, null, null,
      null, null, "ENUMC", "CLASS", null,
      null, null
};


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseElider(LspBaseFile file)
{
   for_file = file;
   elide_rdata = new TreeSet<>();
   
   Object prop = file.getProject().getLanguageData().getCapability(TOKEN_TYPES);
   JSONArray jarr = (JSONArray) prop;
   token_types = new String[jarr.length()];
   for (int i = 0; i < jarr.length(); ++i) {
      token_types[i] = jarr.getString(i);
    }
   Object prop1 = file.getProject().getLanguageData().getCapability(TOKEN_MODS);
   jarr = (JSONArray) prop1;
   token_modifiers = new String[jarr.length()];
   for (int i = 0; i < jarr.length(); ++i) {
      token_modifiers[i] = jarr.getString(i);
    }
}



/********************************************************************************/
/*                                                                              */
/*      Manage elide data                                                       */
/*                                                                              */
/********************************************************************************/

void clearElideData()
{
   elide_rdata.clear();
}



void addElideRegion(int soff,int eoff)
{
   ElideRegion er = new ElideRegion(soff,eoff);
   elide_rdata.add(er);
}


void noteEdit(int soff,int len,int rlen)
{
   for (Iterator<ElideRegion> it = elide_rdata.iterator(); it.hasNext(); ) {
      ElideRegion ed = it.next();
      if (!ed.noteEdit(soff,len,rlen)) it.remove();
    }
}



/********************************************************************************/
/*                                                                              */
/*      Compute Elision                                                         */
/*                                                                              */
/********************************************************************************/

boolean computeElision(IvyXmlWriter xw)
{ 
   ElideData data = new ElideData();
   
   LspBaseProject lbp = for_file.getProject();
   LspBaseProtocol proto = lbp.getProtocol();
   proto.sendWorkMessage("textDocument/foldingRange",
         (Object resp,JSONObject err) -> handleFolds(data,resp,err),
         "textDocument",for_file.getTextDocumentId());
   
   handleDecls(data,for_file.getSymbols(),null);
// proto.sendMessage("textDocument/documentSymbol",
//       (Object resp,JSONObject err) -> handleDecls(data,resp,err),
//       "textDocument",for_file.getTextDocumentId());
   
   List<ElideRange> ranges = data.getRanges();
   for (ElideRange range : ranges) {
      proto.sendWorkMessage("textDocument/semanticTokens/range",
            (Object resp,JSONObject err) -> handleTokens(data,range,resp,err),
            "textDocument",for_file.getTextDocumentId(),
            "range",proto.createRange(for_file,range.getStartOffset(),
                  range.getEndOffset()));
    }
   
   // might want to get hint data
   // might want to check relevance of elision after each query to cut work
  
   data.outputElision(xw);
   
   return true;
}




void handleFolds(ElideData data,Object resp,JSONObject err)
{ 
   JSONArray folds = (JSONArray) resp;
   for (int i = 0; i < folds.length(); ++i) {
      JSONObject fold = folds.getJSONObject(i);
      int soff = for_file.mapLspLineToOffset(fold.getInt("startLine"));
      int eoff = for_file.mapLspLineCharToOffset(fold.getInt("endLine"),
            fold.getInt("endCharacter"));
      if (!data.isRelevant(soff,eoff)) continue;
      String kind = fold.optString("kind","region");
      String typ = null;
      switch (kind) {
         case "imports" :
            typ = "IMPORT";
            break;
         case "comment" :
            break;
         case "region" :
            typ = "STMT";
            break;
       }
      ElideNode en = data.addNode(soff,eoff);
      if (typ != null) en.setNodeType(typ);
    }
}


void handleDecls(ElideData data,Object resp,JSONObject err)
{
   if (resp == null) return;
   
   JSONArray decls = (JSONArray) resp;
   for (int i = 0; i < decls.length(); ++i) {
      JSONObject decl = decls.getJSONObject(i);
      handleDecl(data,decl);
      JSONArray children = decl.optJSONArray("children");
      if (children != null) handleDecls(data,children,null);
    }
}


private void handleDecl(ElideData data,JSONObject decl)
{
   JSONObject range = decl.getJSONObject("range");
   int soff = for_file.mapRangeToLineStartOffset(range);
   int eoff = for_file.mapRangeToEndOffset(range);
   if (!data.isRelevant(soff,eoff)) return;
   
   String ntype = ElideDeclTypes[decl.getInt("kind")];
   if (ntype == null) return;
   
   ElideNode en = data.addNode(soff,eoff);
   en.setNodeType(ntype);
   // TODO:  get File prefix and add it to name
   String name = decl.getString("name");
   String det = decl.optString("detail","");
   String fpfx = for_file.getProject().getRelativeFile(for_file);
   en.setSymbolName(fpfx + ";" + name + det);
}



void handleTokens(ElideData edata,ElideRange range,Object resp,JSONObject err)
{   
   JSONObject data = (JSONObject) resp;
   JSONArray arr = data.getJSONArray("data");
   int line = 0;
   int col = 0;
   for (int i = 0; i < arr.length(); i += 5) {
      int dline = arr.getInt(i+0);
      line += dline;
      if (dline > 0) col = 0;
      col += arr.getInt(i+1);
      int len = arr.getInt(i+2);
      int soff = for_file.mapLspLineCharToOffset(line,col);
      int eoff = soff + len;
      
      
      String typ = token_types[arr.getInt(i+3)];
      Set<String> modset = new HashSet<>();
      int modbits = arr.getInt(i+4);
      String mods = "";
      if (modbits != 0) {
         for (int j = 0; j < token_modifiers.length; ++j) {
            if ((modbits & (1<<j)) != 0) {
               mods += ";" + token_modifiers[j];
               modset.add(token_modifiers[j]);
             }
          }
       }
      LspLog.logD("TOKEN " + line + " " + col + " " + len + " " + typ + " " + mods);
      
      if (!edata.isRelevant(soff,eoff)) continue;
      String st = getSymbolType(typ,modset);
      if (st == null) continue;
      String nt = getNodeType(typ,modset);
      ElideNode en = edata.addNode(soff,eoff);
      en.setSymbolType(st);  
      en.setNodeType(nt);
      if (st.contains("DECL")) {
         ElideNode par = en.getParent();
         en.setSymbolName(par.getSymbolName());
       }
    }
}



/********************************************************************************/
/*                                                                              */
/*      Creating types for elision elements                                     */
/*                                                                              */
/********************************************************************************/

String getSymbolType(String typ,Set<String> mods)
{
   String t = null;
   
   switch (typ) {
      case "type" :
      case "class" :
      case "enum" :
      case "interface" :
      case "struct" :
      case "typeParameter" :
         t = "TYPE";
         if (mods.contains("constructor")) {
            if (mods.contains("declaration")) {
               t = "METHODDECL";
             }
          }
         else if (mods.contains("declaration")) {
            t = "CLASSDECL";
            if (mods.contains("static")) t += "S";
          }
         break;
         
      case "parameter" :
         t = "VARIABLE";
         if (mods.contains("declaration")) {
            t = "PARAMDECL";
          }
         else if (mods.contains("label")) {
            t = "LABEL";
          }
         break;
         
      case "variable" :
         t = "VARIABLE";
         if (mods.contains("declaration")) {
            t = "VARDECL";
          }
         break;

      case "property" :
         if (mods.contains("annotation")) t = "ANNOT";
         else if (mods.contains("static")) t = "FIELDS";
         else if (mods.contains("instance")) t = "FIELD";
         else t = "FIELDC";
         break;
         
      case "enumMember" :
         t = "ENUMC";
         break;
         
      case "function" :
      case "method" :
         t = "CALL";
         if (mods.contains("declaration")) {
            t = "METHODDECL";
          }
         if (mods.contains("static")) t += "S";
         else if (mods.contains("annotation")) t += "A";
         break;
         
      case "keyword" :
         if (mods.contains("control")) t = "KEYWORD";
         break;
         
      case "decorator" :
         t = "ANNOT";
         break;
         
      case "event" :
      case "macro" :
      case "modifier" :
      case "comment" :
      case "string" :
      case "number" :
      case "regexp" :
      case "operator" :
      case "namespace" :
      case "boolean" :
         break;
    }
   
   return t;
}


private String getNodeType(String typ,Set<String> mods)
{
   String t = null;
   
   switch (typ) {
      case "function" :
      case "method" :
         if (!mods.contains("declaration")) {
            t = "CALL";
            if (mods.contains("deprecated")) t += "D";
            if (mods.contains("abstract")) t += "A";
            else if (mods.contains("static")) t += "S";
          }
         break;
    }
   
   return t;
}


/********************************************************************************/
/*                                                                              */
/*      Classes for information about elision requests                          */
/*                                                                              */
/********************************************************************************/

private static class ElideRegion implements Comparable<ElideRegion> {

   private int start_offset;
   private int end_offset;
   
   ElideRegion(int soff,int eoff) {
      start_offset = soff;
      end_offset = eoff;
    }
   
   int getStartOffset()                         { return start_offset; }
   int getEndOffset()                           { return end_offset; }
   
   boolean noteEdit(int soff,int len,int rlen) {
      if (end_offset <= soff) ; 			// before the change
      else if (start_offset > soff + len - 1) { 	// after the change
         start_offset += rlen - len;
         end_offset += rlen - len;
       }
      else if (start_offset <= soff && end_offset >= soff+len-1) {	// containing the change
         end_offset += rlen -len;
       }
      else return false;				     // in the edit -- remove it
      return true;
    }
   
   @Override public int compareTo(ElideRegion r) {
      if (start_offset < r.start_offset) return -1;
      else if (start_offset > r.start_offset) return 1;
      if (end_offset < r.end_offset) return 1;
      else if (end_offset > r.end_offset) return -1;
      return 0;
    }

}	// end of inner abstract class ElideRegion




/********************************************************************************/
/*                                                                              */
/*      Elision areas                                                           */
/*                                                                              */
/********************************************************************************/

List<ElideRange> computeRanges()
{
   List<ElideRange> rslt = new ArrayList<>();
   
   // assumes regions are sorted so containers come before possible children
   for (ElideRegion rgn : elide_rdata) {
      boolean fnd = false;
      for (ElideRange range : rslt) {
         if (range.extend(rgn.getStartOffset(),rgn.getEndOffset())) {
            fnd = true;
            break;
          }
       }
      if (!fnd) {
         ElideRange nrange = new ElideRange(rgn.getStartOffset(),rgn.getEndOffset());
         rslt.add(nrange);
       }
    }
   
   return rslt;
}



private static class ElideRange {
   
   private int start_offset;
   private int end_offset;
   
   ElideRange(int soff,int eoff) {
      start_offset = soff;
      end_offset = eoff;
    }
   
   int getStartOffset()                         { return start_offset; }
   int getEndOffset()                           { return end_offset; }
   
   boolean overlaps(int soff,int eoff) {
      if (start_offset >= eoff) return false;
      if (end_offset <= soff) return false;
      return true;
    }
   
   boolean extend(int soff,int eoff) {
      if (overlaps(soff,eoff)) {
         start_offset = Math.min(start_offset,soff);
         end_offset = Math.max(end_offset,eoff);
         return true;
       }
      return false;
    }
   
}       // end of inner class ElideRange



/********************************************************************************/
/*                                                                              */
/*      Tree Nodes for elision                                                  */
/*                                                                              */
/********************************************************************************/

private static class ElideNode implements Comparable<ElideNode> {
   
   private int start_offset;
   private int end_offset;
   private double node_priority;
   private Set<ElideNode> child_nodes;
   private ElideNode parent_node;
   private String node_type;
   private String symbol_type;
   private String symbol_name;
   
   ElideNode(int start,int end) {
      start_offset = start;
      end_offset = end;
      node_priority = 1.0;
      child_nodes = null;
      node_type = null;
      symbol_type = null;
      symbol_name = null;
      parent_node = null;
    }
  
   int getStartOffset()                         { return start_offset; }
   int getEndOffset()                           { return end_offset; }
   String getSymbolName()                       { return symbol_name; }
   ElideNode getParent()                        { return parent_node; }
   
   void setNodeType(String nt)                  { node_type = nt; }
   void setSymbolType(String st)                { symbol_type = st; }
   void setSymbolName(String nm)                { symbol_name = nm; }
   
   boolean contains(ElideNode node) {
      return (start_offset <= node.start_offset && end_offset >= node.end_offset);
    }
   
   
   
   void addChild(ElideNode cn) {
      if (child_nodes == null) child_nodes = new TreeSet<>();
      child_nodes.add(cn);
      cn.parent_node = this;
    }
   
   void addNode(ElideNode child,int start,int end) {
      if (child_nodes != null) {
         for (Iterator<ElideNode> it = child_nodes.iterator(); it.hasNext(); ) {
            ElideNode cn = it.next();
            if (cn.getEndOffset() < start) continue;
            if (cn.getStartOffset() > end) break;
            if (cn.contains(child)) {
               cn.addNode(child,start,end);
               return;
             }
            else if (child.contains(cn)) {
               it.remove();
               child.addChild(cn);
             }
          }
       }
      addChild(child);
    }
   
   @Override public int compareTo(ElideNode node) {
      if (start_offset < node.start_offset) return -1;
      else if (start_offset > node.start_offset) return 1;
      else if (end_offset > node.end_offset) return -1;
      else if (end_offset < node.end_offset) return 1;
      else return hashCode() - node.hashCode();
    }
   
   void computePriority(double p) {
      node_priority = p;
      if (child_nodes != null) {
         double p1 = p * DOWN_PRIORITY;
         for (ElideNode cn : child_nodes) {
            cn.computePriority(p1);
            p1 *= CHILD_PRIORITY;
          }
       }
    }
   
   void outputXml(IvyXmlWriter xw) {
      xw.begin("ELIDE");
      xw.field("START",start_offset);
      xw.field("LENGTH",end_offset - start_offset);
      xw.field("PRIORITY",node_priority);
      if (symbol_name != null) xw.field("FULLNAME",symbol_name);
      if (symbol_type != null) xw.field("TYPE",symbol_type);
      if (node_type != null) xw.field("NODE",node_type);
      // handle hint data
      if (child_nodes != null) {
         for (ElideNode cn : child_nodes) {
            cn.outputXml(xw);
          }
       }
      xw.end("ELIDE");
    }
   
}       // end of inner class ElideNode



private class ElideData {
   
   private List<ElideRange> elide_ranges;
   private ElideNode root_node;
   
   ElideData() {
      root_node = new ElideNode(0,for_file.getLength());
      root_node.setNodeType("FILE");
      elide_ranges = computeRanges();
    }
   
   List<ElideRange> getRanges()                 { return elide_ranges; }
   
   boolean isRelevant(int soff,int eoff) {
      for (ElideRange er : elide_ranges) {
         if (er.overlaps(soff,eoff)) return true;
       }
      return false;
    }
   
   ElideNode addNode(int start,int end) { 
      ElideNode child = new ElideNode(start,end);
      root_node.addNode(child,start,end);
      return child;
    }
   
   void outputElision(IvyXmlWriter xw) {
      root_node.computePriority(1.0);
      root_node.outputXml(xw);
    }
   
}       // end of inner class ElideData


}       // end of class LspBaseElider




/* end of LspBaseElider.java */

