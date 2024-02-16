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
import java.util.Stack;
import java.util.TreeSet;

import javax.swing.text.Segment;

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

enum CommentType { NONE, EOL, STARSLASH };

private Set<ElideRegion> elide_rdata;
private LspBaseFile for_file;

private String [] token_types;
private String [] token_modifiers;
private boolean scan_braces;
private boolean scan_calls;
private boolean slashslash_comments;
private boolean slashstar_comments;
private boolean pound_comments;
private boolean text_blocks;            // using triple quotes
private boolean backquote_blocks;
private Segment file_contents;
private boolean doing_elision;
private volatile boolean abort_elision;
private boolean update_contents;




private static double DOWN_PRIORITY = 0.90;
private static double CHILD_PRIORITY = 0.98;

private String [] ElideDeclTypes = {
      null, "COMPUNIT", "MODULE", null, null,
      "CLASS", "METHOD", "ANNOT", "FIELD", "METHOD",
      "ENUM", "CLASS", "METHOD", "VARIABLE", "VARIABLE",
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
   update_contents = true;
   file_contents = null;
   
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
   
   LspBaseLanguageData lld = file.getLanguageData();
   scan_braces = lld.getCapabilityBool("lsp.elision.scanBraces");
   scan_calls = lld.getCapabilityBool("lsp.elision.scanCalls");
   slashslash_comments = lld.getCapabilityBool("lsp.elision.slashslashComments");
   slashstar_comments = lld.getCapabilityBool("lsp.elision.slashstarComments");
   pound_comments = lld.getCapabilityBool("lsp.elision.poundComments");
   text_blocks = lld.getCapabilityBool("lsp.elision.textBlocks");
   backquote_blocks = lld.getCapabilityBool("lsp.elision.backquoteBlocks");
   
   doing_elision = false;
   abort_elision = false;
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
   
   synchronized (this) {
      abort_elision = true;
      update_contents = true;
    }
}



/********************************************************************************/
/*                                                                              */
/*      Compute Elision                                                         */
/*                                                                              */
/********************************************************************************/

boolean computeElision(IvyXmlWriter xw) throws LspBaseException
{ 
  
   synchronized (this) {
      if (doing_elision) {
         LspLog.logD("ELISION ABORT " + for_file.getFile());
         while (doing_elision) {
            abort_elision = true;
            try {
               wait(1000);
             }
            catch (InterruptedException e) { }
          }
       }
      doing_elision = true;
      abort_elision = false;
      if (update_contents) {
         file_contents = for_file.getSegment(0,for_file.getLength());
       }
    }
   
   try {
      LspLog.logD("ELISION START " + scan_braces + " " + scan_calls + " " + for_file.getFile());
      ElideData data = new ElideData();
      
      LspBaseProject lbp = for_file.getProject();
      LspBaseProtocol proto = lbp.getProtocol();
      if (abort_elision) return false;
      LspLog.logD("ELISION FOLDS");
      FoldResponder fr = new FoldResponder(data);
      proto.sendWorkMessage("textDocument/foldingRange",fr,
            "textDocument",for_file.getTextDocumentId());
      
      if (abort_elision) return false;
      LspLog.logD("ELISION DECLS");
      handleDecls(data,for_file.getSymbols(),null);
// proto.sendMessage("textDocument/documentSymbol",
//       (Object resp,JSONObject err) -> handleDecls(data,resp,err),
//       "textDocument",for_file.getTextDocumentId());
      
      List<ElideRange> ranges = data.getRanges();
      Segment textdata = null;
      for (ElideRange range : ranges) {
         if (abort_elision) return false;
         if (scan_calls || scan_braces) {
            int start = range.getStartOffset();
            int end = range.getEndOffset();
            if (end > for_file.getLength()) end = for_file.getLength(); 
            textdata = for_file.getSegment(start,end-start);
          }
         LspLog.logD("ELISION TOKENS");
         TokenResponder tr = new TokenResponder(data,range,textdata);
         proto.sendWorkMessage("textDocument/semanticTokens/range",tr,
               "textDocument",for_file.getTextDocumentId(),
               "range",proto.createRange(for_file,range.getStartOffset(),
                     range.getEndOffset()));
         
         if (abort_elision) return false;
         if (scan_braces) {
            LspLog.logD("ELISION BRACES");
            scanBraces(data,range.getStartOffset(),range.getEndOffset(),textdata);
          }
       }
      
      if (abort_elision) return false;
      data.outputElision(xw);
      
      LspLog.logD("ELISION FINISH");
    }
   finally {
      LspLog.logD("ELISION END");
      synchronized (this) {
         doing_elision = false;
         abort_elision = false;
       }
    }
   
   return true;
}


private class FoldResponder implements LspArrayResponder {
  
   private ElideData elide_data;
   
   FoldResponder(ElideData ed) {
      elide_data = ed;
    }   
   
   @Override public void handleResponse(JSONArray folds) {
      handleFolds(elide_data,folds);
    }

}       // end of inner class FoldResponder



private class TokenResponder implements LspJsonResponder {

   private ElideData elide_data;
   private ElideRange elide_range;
   private Segment text_data;
   
   TokenResponder(ElideData ed,ElideRange er,Segment td) {
      elide_data = ed;
      elide_range = er;
      text_data = td;
    }
   
   @Override public void handleResponse(JSONObject robj) {
      JSONArray data = robj.getJSONArray("data");
      handleTokens(elide_data,elide_range,text_data,data);
    }
   
}       // end of inner class TokenResponder




/********************************************************************************/
/*                                                                              */
/*      Handle top-level folds from lspbase                                     */
/*                                                                              */
/********************************************************************************/

void handleFolds(ElideData data,JSONArray folds)
{ 
   for (int i = 0; i < folds.length(); ++i) {
      if (abort_elision) return;
      JSONObject fold = folds.getJSONObject(i);
      int soff = for_file.mapLspLineToOffset(fold.getInt("startLine"));
      int eoff = for_file.mapLspLineCharToOffset(fold.getInt("endLine"),
            fold.optInt("endCharacter",0)) + 1;
      if (!data.isRelevant(soff,eoff)) continue;
      eoff = fixFoldEndOffset(fold,soff,eoff);
      String kind = fold.optString("kind","other");
      LspLog.logD("ELISION FOLD " + soff + " " + eoff + " " + kind + " " + 
            fold.getInt("startLine") + " " + fold.optInt("startCharacter",0) + " " +
            fold.getInt("endLine") + " " + fold.optInt("endCharacter",0));
                  
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
         default :
            continue;                           // might want type = stmt or ignore
       }
      data.addNode(soff,eoff,typ,null);
    }
}


private int fixFoldEndOffset(JSONObject fold,int soff,int eoff)
{
   try {
      int scol = fold.optInt("startCharacter",0)+1;
      if (soff + scol - 1 >= file_contents.length()) return eoff;
      
      char prevch = file_contents.charAt(soff+scol-1);
      if (eoff > file_contents.length()) eoff = file_contents.length();
      if (prevch == '\n' && scol > 0) {
         char prevech = file_contents.charAt(eoff-1);
         if (prevech == '\n') {
            for (int i = eoff; i < file_contents.length(); ++i) {
               char ch = file_contents.charAt(i);
               if (ch == '\n') {
                  return i+1;
                }
               else if (!Character.isWhitespace(ch)) break;
             }
          }
       }
    }
   catch (Throwable t) {
      LspLog.logE("Problem fixing fold end offset " +
            soff + " " + eoff + " " + file_contents.length() +
            fold.optInt("startCharacter",0),t);
    }
   return eoff;
}



/********************************************************************************/
/*                                                                              */
/*      Handle declarations for this file                                       */
/*                                                                              */
/********************************************************************************/

void handleDecls(ElideData data,Object resp,JSONObject err)
{
   if (resp == null) return;
   
   JSONArray decls = (JSONArray) resp;
   for (int i = 0; i < decls.length(); ++i) {
      if (abort_elision) return;
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
   
   soff = fixDeclStartOffset(soff,ntype);
   
   ElideNode en = data.addNode(soff,eoff,ntype,null);
   // TODO:  get File prefix and add it to name
   String name = decl.getString("name");
   String det = decl.optString("detail","");
   String fpfx = for_file.getProject().getRelativeFile(for_file);
   en.setSymbolName(fpfx + ";" + name + det);
}



private int fixDeclStartOffset(int soff,String ntype)
{
   switch (ntype) {
      case "METHOD" :
      case "CLASS" :
      case "ANNOT" :
      case "FIELD" :
      case "ENUM" :
      case "VARIABLE" :
         break;
      default :
         return soff;
    }
   
   // include prior annotations as part of declaration
   int off = soff;
   boolean lstart = true;
   for ( ; off >= 0; off--) {
      char c = file_contents.charAt(off);
      if (Character.isWhitespace(c)) {
         if (lstart && c == '\n') lstart = false;
         else if (lstart) soff = off;
       }
      else if (Character.isJavaIdentifierPart(c)) ;
      else if (c == '}' || c == ';' || 
            c == '{' || c == ',') break;
      else if (c == '@') {
         soff = off;
         lstart = true;
       }
    }
   
   return soff;
}



/********************************************************************************/
/*                                                                              */
/*      Handle token information                                                */
/*                                                                              */
/********************************************************************************/

void handleTokens(ElideData edata,ElideRange range,Segment textdata,JSONArray arr)
{   
   int line = 0;
   int col = 0;
   for (int i = 0; i < arr.length(); i += 5) {
      if (abort_elision) return;
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
      if (typ.equals("function") || typ.equals("method")) {
         if (!modset.contains("declaration")) {
            scanCallBlock(edata,soff,eoff,textdata,range.getStartOffset());
          }
       }
         
      ElideNode en = edata.addNode(soff,eoff,null,null);
      en.setSymbolType(st);  
      if (st.contains("DECL")) {
         ElideNode par = en.getParent();
         if (par != null) {
            en.setSymbolName(par.getSymbolName());
          }
       }
    }
}



/********************************************************************************/
/*                                                                              */
/*      Handle scanning for braces                                              */
/*                                                                              */
/********************************************************************************/

private void scanBraces(ElideData edata,int soff,int eoff,Segment text)
{
   int textln = text.length();
 
   Stack<Integer> starts = new Stack<>();
   
   for (int i = 0; i < textln; ++i) {
      char c = text.charAt(i);
      switch (c) {
         case '#' :
         case '/' :
            int j0 = skipComment(i,c,text);
            if (j0 > 0) {
               i = j0;
             }
            break;
         case '"' :
         case '`' :
         case '\'' :
            int j1 = skipString(i,c,text);
            if (j1 > 0) {
               i = j1;
             }
            break;
         case '{' :
            starts.push(i);
            break;
         case '}' :
            if (!starts.isEmpty()) {
               int s0 = starts.pop();
               addScanBlock(edata,s0+soff,i+1+soff,"BLOCK");
             }
            break;
       }     
    }
}



private ElideNode scanCallBlock(ElideData edata,int soff,int eoff,Segment text,int segstart)
{
   LspLog.logD("SCAN CALL " + soff + " " + eoff + " " + segstart);
   int seglen = text.length();
   int lpr = 0;
   for (int i = eoff-segstart; i < seglen; ++i) {
      char c = text.charAt(i);
      if (c == '(') {
         lpr = i;
         break;
       }
      else if (Character.isWhitespace(c)) continue;
      else return null;
    }
   LspLog.logD("CALL LPR " + lpr + " " + (lpr+segstart));
   
   int lvl = 0;
   for (int j = lpr; j < seglen; ++j) {
      char c = text.charAt(j);
      switch (c) {
         case '#' :
         case '/' :
            int j0 = skipComment(j,c,text);
            if (j0 > 0) {
               j = j0;
             }
            break;
         case '"' :
         case '`' :
         case '\'' :
            int j1 = skipString(j,c,text);
            if (j1 > 0) {
               j = j1;
             }
            break; 
         case '(' :
            ++lvl;
            break;
         case ')' :
            --lvl;
            if (lvl == 0) {
               LspLog.logD("CALL NODE " + soff + " " + (j+1+segstart));
               ElideNode en = addScanBlock(edata,soff,j+1+segstart,"CALL");
               en.setHintLocation(lpr+segstart);
               return en;
             }
            break;
       }
    }
   
   return null;
}



private int skipComment(int idx,char c,Segment text)
{
   if (idx+1 >= text.length()) return -1;
   char c1 = text.charAt(idx+1);
   // handle one line commemts
   if ((slashslash_comments && c == '/' && c1 == '/') ||
         (pound_comments && c == '#')) {
       while (idx < text.length()) {
          c = text.charAt(idx);
          ++idx;
          if (c == '\n') break;
        }
       return idx;
    }
   else if (slashstar_comments && c == '/' && c1 == '*' && idx+3 < text.length()){
      c1 = text.charAt(idx+2);
      for (idx = idx+2; idx+2 < text.length(); ++idx) {
         c = c1;
         c1 = text.charAt(idx+1);
         if (c == '*' && c1 == '/') {
            idx += 2;
            break;
          }
       }
      return idx;
    }
   
   return -1;
}



private int skipString(int idx,char c0,Segment text)
{
   boolean endateol = true;
   int endcount = 1;
   if (c0 == '`') {
      if (!backquote_blocks) return -1;
      else endateol = false;
    }
   else if (idx+3 < text.length() && text_blocks && 
         text.charAt(idx+1) == c0 && text.charAt(idx+2) == c0) {
      idx +=2;
      endateol = false;
      endcount = 3;
    }
    
   int fnd = 0;
   for (idx = idx+1; idx+1 < text.length(); ++idx) {
      char c = text.charAt(idx);
      if (c == '\\') {
         ++idx;
         fnd = 0;
       }
      else if (c == '\n' && endateol) break;
      else if (c == c0) {
         ++fnd;
         if (endcount == fnd) break;
       }
      else fnd = 0;
    }
   
   return idx;
}



private ElideNode addScanBlock(ElideData edata,int soff,int eoff,String nodetype)
{
   ElideNode en = edata.addNode(soff,eoff,nodetype,null);
   return en;
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

private class ElideNode implements Comparable<ElideNode>, LspJsonResponder {
   
   private int start_offset;
   private int end_offset;
   private double node_priority;
   private Set<ElideNode> child_nodes;
   private ElideNode parent_node;
   private String node_type;
   private String symbol_type;
   private String symbol_name;
   private JSONObject signature_data;
   private int hint_location;
   
   ElideNode(int start,int end,String type,String stype) {
      start_offset = start;
      end_offset = end;
      node_priority = 1.0;
      child_nodes = null;
      node_type = type;
      symbol_type = stype;
      symbol_name = null;
      parent_node = null;
      hint_location = -1;
      
      LspLog.logD("CREATE NODE " + type + " " + stype + " " +  start + " " + end);
    }
  
   int getStartOffset()                         { return start_offset; }
   int getEndOffset()                           { return end_offset; }
   String getSymbolName()                       { return symbol_name; }
   ElideNode getParent()                        { return parent_node; }
   
   void setNodeType(String nt)                  { node_type = nt; }
   void setSymbolType(String st)                { symbol_type = st; }
   void setSymbolName(String nm)                { symbol_name = nm; }
   void setHintLocation(int loc)                { hint_location = loc; }
   
   boolean contains(ElideNode node) {
      return (start_offset <= node.start_offset && end_offset >= node.end_offset);
    }
   
   boolean overlaps(ElideNode node) {
      if (start_offset >= node.getEndOffset()) return false;
      if (end_offset <= node.getStartOffset()) return false;
      return true;
    }
   
   ElideNode addNode(ElideNode child,int start,int end) {
      if (child_nodes != null) {
         for (Iterator<ElideNode> it = child_nodes.iterator(); it.hasNext(); ) {
            ElideNode cn = it.next();
            if (cn.getEndOffset() < start) continue;
            if (cn.getStartOffset() > end) break;
            if (cn.contains(child)) {
               return cn.addNode(child,start,end);
             }
            else if (child.contains(cn)) {
               it.remove();
               child.addChild(cn);
             }
            else if (cn.overlaps(child)) {
               LspLog.logE("Overlapping node add " + start + " " + end + " " +
                     cn.start_offset + " " + cn.end_offset);
               // overlap -- ignore; first restore any removed children
               if (cn.child_nodes != null) {
                  for (ElideNode en1 : cn.child_nodes) {
                     addChild(en1);
                   }
                }
               return null;
             }
          }
       }
      addChild(child);
      return child;
    }
   
   private void addChild(ElideNode cn) {
      if (child_nodes == null) child_nodes = new TreeSet<>();
      child_nodes.add(cn);
      cn.parent_node = this;
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
      if (hint_location >= 0) outputHintData(xw);
      if (child_nodes != null) {
         for (ElideNode cn : child_nodes) {
            cn.outputXml(xw);
          }
       }
      xw.end("ELIDE");
    }
   
   private void outputHintData(IvyXmlWriter xw) {
      if ("CALL".equals(node_type)) {
         LspBaseProject lbp = for_file.getProject();
         LspBaseProtocol proto = lbp.getProtocol();
         JSONObject ctx = createJson("triggerKind",1,"isRetrigger",false);
         signature_data = null;
         try {
            proto.sendMessage("textDocument/signatureHelp",this,
                  "textDocument",for_file.getTextDocumentId(),
                  "position",proto.createPosition(for_file,hint_location+1),
                  "context",ctx);
          }
         catch (LspBaseException e) { }
         if (signature_data != null) {
            JSONArray params = signature_data.optJSONArray("parameters");
            if (params != null) {
               xw.begin("HINT");
               xw.field("KIND","METHOD");
               // xw.field("RETURNS",<<return type>>);
               // xw.field("CONSTUCTOR",true);
               xw.field("NUMPARAM",params.length());
               for (int i = 0; i < params.length(); ++i) {
                  JSONObject param = params.getJSONObject(i);
                  String label = param.getString("label");
                  xw.begin("PARAMETER");
                  int idx = label.lastIndexOf(" ");
                  if (idx > 0) {
                     xw.field("NAME",label.substring(idx+1));
                     xw.field("TYPE",label.substring(0,idx));
                   }
                  else {
                     xw.field("NAME",label);
                   }
                  xw.end("PARAMETER");
                }
               xw.end("HINT");
             }
          }
       }
    }
   
   @Override public void handleResponse(JSONObject help) {
      if (help.isNull("signatures")) return;
      JSONArray sigs = help.getJSONArray("signatures");
      if (sigs.length() == 0) return;
      int idx = help.optInt("activeSignature",0);
      if (idx < 0 || idx >= sigs.length()) idx = 0;
      signature_data = sigs.getJSONObject(idx);
    }
   
}       // end of inner class ElideNode



private class ElideData {
   
   private List<ElideRange> elide_ranges;
   private ElideNode root_node;
   
   ElideData() {
      root_node = new ElideNode(0,for_file.getLength(),null,null);
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
   
   ElideNode addNode(int start,int end,String nodetype,String symtype) { 
      ElideNode child = new ElideNode(start,end,nodetype,symtype);
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

