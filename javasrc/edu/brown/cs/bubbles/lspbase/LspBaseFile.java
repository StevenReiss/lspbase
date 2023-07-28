/********************************************************************************/
/*										*/
/*		LspBaseFile.java						*/
/*										*/
/*	Information about a file						*/
/*										*/
/********************************************************************************/
/*	Copyright 2011 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 * This program and the accompanying materials are made available under the	 *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at								 *
 *	http://www.eclipse.org/legal/epl-v10.html				 *
 *										 *
 ********************************************************************************/



package edu.brown.cs.bubbles.lspbase;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.text.BadLocationException;
import javax.swing.text.GapContent;
import javax.swing.text.Position;
import javax.swing.text.Segment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.file.IvyFormat;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseFile implements LspBaseConstants
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private File for_file;
private LspBaseProject for_project;
// private StringBuffer file_contents;
private String file_language;
private volatile int file_version;
private LspBaseLineOffsets line_offsets;
private GapContent file_contents;
private boolean is_changed;
private LspBaseElider file_elider;
private JSONArray file_symbols;
private Set<String> base_ids;
private Map<String,PrivateBuffer> private_buffers;
private String current_editor;

private static String [] token_types;
private static String [] token_modifiers;




/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

LspBaseFile(LspBaseProject proj,File f,String lang)
{
   for_file = f;
   for_project = proj;
   file_contents = null;
   file_language = lang;
   file_version = 0;
   line_offsets = null;
   is_changed = false;
   file_elider = null;
   file_symbols = null;
   base_ids = new HashSet<>();
   private_buffers = new HashMap<>();
}


private void setupTokens()
{
   if (token_types == null) {
      Object prop = getProject().getLanguageData().getCapability(TOKEN_TYPES);
      if (prop == null) return;
      JSONArray jarr = (JSONArray) prop;
      token_types = new String[jarr.length()];
      for (int i = 0; i < jarr.length(); ++i) {
	 token_types[i] = jarr.getString(i);
       }
      Object prop1 = getProject().getLanguageData().getCapability(TOKEN_MODS);
      jarr = (JSONArray) prop1;
      token_modifiers = new String[jarr.length()];
      for (int i = 0; i < jarr.length(); ++i) {
	 token_modifiers[i] = jarr.getString(i);
       }
    }
}


/********************************************************************************/
/*										*/
/*	Access methods								*/
/*										*/
/********************************************************************************/

String getPath()		{ return for_file.getPath(); }
File getFile()			{ return for_file; }
String getLanguage()		{ return file_language; }

LspBaseLanguageData getLanguageData()
{
   LspBaseMain lspbase = LspBaseMain.getLspMain();
   return lspbase.getLanguageData(getLanguage());
}
String getUri() 		{ return getUri(for_file); }
LspBaseProject getProject()	{ return for_project; }
boolean hasChanged()		{ return is_changed; }
int getTabSize()		{ return 8; }

synchronized LspBaseElider getElider()
{
   if (file_elider == null) {
      file_elider = new LspBaseElider(this);
    }
   return file_elider;
}

String getContents() {
   loadContents();
   try {
      return file_contents.getString(0,file_contents.length());
    }
   catch (BadLocationException e) {
      LspLog.logE("Problem getting file contents " + e);
      return "";
    }
}


Segment getSegment(int off0,int len)
{ 
   return getSegment(off0,len,null);
}
   

Segment getSegment(int off0,int len,Segment seg)   
{
   loadContents();
   if (seg == null) seg = new Segment();
   try {
      file_contents.getChars(off0,len,seg);
      return seg;
    }
   catch (BadLocationException e) { 
      LspLog.logE("Bad segment get ",e);
    }

   return null;
}


String getText(int off,int len)
{
   try {
      return file_contents.getString(off,len);
    }
   catch (BadLocationException e) { }
   return null;
}


private synchronized void loadContents()
{
   if (file_contents == null) {
      try {
	 String cnts = IvyFile.loadFile(for_file);
	 GapContent gc = new GapContent(cnts.length()+1024);
	 gc.insertString(0,cnts);
	 file_contents = gc;
       }
      catch (Exception e) {
	 file_contents = new GapContent();
       }
    }
}



int getLength()
{
   if (file_contents == null) getContents();

   return file_contents.length();
}


JSONObject getTextDocumentItem()
{
   if (file_version == 0) file_version = 1;

   JSONObject json = new JSONObject();
   json.put("uri",getUri());
   json.put("languageId",file_language);
   json.put("version",file_version);
   json.put("text",getContents());

   return json;
}


JSONObject getTextDocumentId()
{
   JSONObject json = new JSONObject();
   json.put("uri",getUri());
   if (file_version > 0) json.put("version",file_version);
   return json;
}




/********************************************************************************/
/*										*/
/*	File locking								*/
/*										*/
/********************************************************************************/

synchronized void lockFile(String bid)
{ 
   while (current_editor != null) {
      try {
         wait(5000);
       }
      catch (InterruptedException e) { }
    }
   current_editor = bid;
}

synchronized void unlockFile()
{
   current_editor = null;
   notifyAll();
}


/********************************************************************************/
/*										*/
/*	Line -- Position mapping (line/char are 1-based)			*/
/*										*/
/********************************************************************************/

int mapLspLineToOffset(int line)
{
   if (line_offsets == null) setupOffsets();
   
   return line_offsets.findOffset(line+1);
}

int mapLineToOffset(int line)
{
   if (line_offsets == null) setupOffsets();
   
   return line_offsets.findOffset(line);
}


int mapLspLineCharToOffset(int line, int col)
{
   return mapLineCharToOffset(line+1,col+1);
}



int mapLineCharToOffset(int line,int cpos)
{
   if (line_offsets == null) setupOffsets();

   int lstart = line_offsets.findOffset(line);

   return lstart+cpos-1;
}


int mapLineCharToOffset(JSONObject position)
{
   return mapLspLineCharToOffset(position.getInt("line"),
	 position.getInt("character"));
}


int mapLineToOffset(JSONObject position)
{
   return mapLspLineToOffset(position.getInt("line"));
}



int mapRangeToStartOffset(JSONObject range)
{
   return mapLineCharToOffset(range.getJSONObject("start"));
}


LineCol mapRangeToStartLspLineCol(JSONObject range)
{
   return mapPositionToLspLineCol(range.getJSONObject("start"));
}

LineCol mapRangeToEndLspLineCol(JSONObject range)
{
   return mapPositionToLspLineCol(range.getJSONObject("end"));
}

LineCol mapPositionToLspLineCol(JSONObject pos)
{
   return new LineCol(pos.getInt("line"),pos.getInt("character"));
}




int mapRangeToLineStartOffset(JSONObject range)
{
   return mapLineToOffset(range.getJSONObject("start"));
}


int mapRangeToEndOffset(JSONObject range)
{
   return mapLineCharToOffset(range.getJSONObject("end"));
}


int mapRangeToLineEndOffset(JSONObject range)
{
   return mapLineToOffset(range.getJSONObject("end"));
}


int mapOffsetToLine(int offset)
{
   if (line_offsets == null) setupOffsets();

   int line = line_offsets.findLine(offset);

   return line;
}


LineCol mapOffsetToLineColumn(int offset)
{
   if (line_offsets == null) setupOffsets();

   int line = line_offsets.findLine(offset);
   int lstart = line_offsets.findOffset(line);

   return new LineCol(line,offset-lstart+1);
}


LineCol mapOffsetToLspLineColumn(int offset)
{
   if (line_offsets == null) setupOffsets();

   int line = line_offsets.findLine(offset);
   int lstart = line_offsets.findOffset(line);

   return new LineCol(line-1,offset-lstart);
}


private synchronized void setupOffsets()
{
   String newline = "\n";

   if (line_offsets == null) {
      if (file_contents == null) {
	 try {
	    line_offsets = new LspBaseLineOffsets(newline,
		  new FileReader(for_file));
	  }
	 catch (IOException e) {
	    line_offsets = new LspBaseLineOffsets(newline,new StringReader(""));
	  }
       }
      else {
	 String cnts = getText(0,file_contents.length());
	 line_offsets = new LspBaseLineOffsets(newline,
	       new StringReader(cnts));
       }
    }
}



/********************************************************************************/
/*										*/
/*	Document Symbols methods						*/
/*										*/
/********************************************************************************/

JSONArray getSymbols()
{
   if (file_symbols == null) {
      file_symbols = new JSONArray();
      LspBaseProtocol proto = for_project.getProtocol();
      try {
         proto.sendWorkMessage("textDocument/documentSymbol",
               this::handleSymbols,
               "textDocument",getTextDocumentId());
       }
      catch (LspBaseException e) {
         LspLog.logE("Problem getting document symbols",e);
       }
      if (for_project.getLanguageData().getCapabilityBool("lsp.fileModule")) {
	 String nm = for_file.getName();
	 int idx = nm.lastIndexOf(".");
	 if (idx > 0) nm = nm.substring(0,idx);
	 JSONObject rng = proto.createRange(this,0,getLength());
	 JSONObject obj = createJson("name","","kind",2,"range",rng,
	       "selectionRange",rng);
	 addSymbolForFile(obj,null);
       }
    }
   return file_symbols;
}


JSONObject findSymbol(JSONArray syms,String name)
{
   if (syms == null) return null;

   int idx = name.indexOf(".");
   String lookfor = name;
   if (idx > 0) {
      lookfor = name.substring(0,idx);
      name = name.substring(idx+1);
    }
   else name = null;
   for (int i = 0; i < syms.length(); ++i) {
      JSONObject sym = syms.getJSONObject(i);
      if (sym.getString("name").equals(lookfor)) {
	 if (name == null) return sym;
	 else return findSymbol(sym.optJSONArray("children"),name);
       }
    }
   return null;
}


private void handleSymbols(JSONArray jarr)
{
   for (int i = 0; i < jarr.length(); ++i) {
      addSymbolForFile(jarr.getJSONObject(i),null);
    }
}


private void addSymbolForFile(JSONObject sym,String pfx)
{
   if (pfx != null) {
      sym.put("prefix",pfx);
    }
   file_symbols.put(sym);
   JSONArray children = sym.optJSONArray("children");
   if (children != null) {
      String nm = sym.getString("name");
      if (pfx == null) pfx = nm;
      else pfx = pfx + "." + nm;
      for (int i = 0; i < children.length(); ++i) {
	 addSymbolForFile(children.getJSONObject(i),pfx);
       }
      sym.put("children",JSONObject.NULL);
      sym.put("nested",children);
    }
}


void clearSymbols()
{
   file_symbols = null;
}


/********************************************************************************/
/*										*/
/*	Editing methods 							*/
/*										*/
/********************************************************************************/

void open(String bid) throws LspBaseException
{
   if (bid != null && !bid.startsWith("*")) base_ids.add(bid);

   if (file_version > 0) return;

   getContents();

   for_project.openFile(this);
   file_version = 1;
}


void close(String bid) 
{
   base_ids.remove(bid);
   if (base_ids.isEmpty()) {
      try {
         for_project.closeFile(this);
       }
      catch (LspBaseException e) {
         LspLog.logE("Problem closing file",e);
       }
      file_contents = null;
      file_version = -1;
    }
}




void refreshFile() throws LspBaseException
{
   file_contents = null;
   ++file_version;
   for_project.closeFile(this);
   loadContents();
   for_project.openFile(this);
   int len = file_contents.length(); 
   try {
      String txt = file_contents.getString(0,len);
      for (String bid : base_ids) {
	 sendEditToBubbles(bid,0,len,txt);
       }
    }
   catch (BadLocationException e) { }
}



private void sendEditToBubbles(String bid,int off,int len,String txt)
{
   LspBaseMain lspmain = LspBaseMain.getLspMain();
   IvyXmlWriter msg = lspmain.beginMessage("EDIT",bid);
   msg.field("FILE",getPath());
   msg.field("LENGTH",len);
   msg.field("OFFSET",off);
   if (off == 0 && len == file_contents.length() && txt != null && len > 0) {
      msg.field("COMPLETE",true);
      byte [] data = txt.getBytes();
      msg.bytesElement("CONTENTS",data);
    }
   else if (txt != null) {
      msg.cdata(txt);
    }
   lspmain.finishMessageWait(msg,500);
}


void reload()
{ }


void edit(String bid,int id,List<LspBaseEdit> edits) throws LspBaseException
{
   // compare id with current version number

   if (file_version <= 0) {
      open(bid);
    }

   LspBaseProtocol proto = getProject().getProtocol();
   boolean chng = false;
   int ver = file_version;

   Collections.sort(edits);
   
   lockFile(bid);
   try {
      JSONArray changes = new JSONArray();
      try {
         for (LspBaseEdit edit : edits) {
            int len = edit.getLength();
            int off = edit.getOffset();
            int tlen = 0;
            String text = edit.getText();
            String txt = (text == null ? "" : text);
            LspLog.logD("EDIT BUFFER " + off + " " + len + " " + IvyFormat.formatString(txt));
            
            JSONObject rng = proto.createRange(this,off,off+len);
            JSONObject rchng = createJson("range",rng,"text",txt);
            changes.put(rchng);
            
            if (len > 0) {
               file_contents.remove(off,len);
             }
            if (text != null && text.length() > 0) {
               file_contents.insertString(off,text);
               tlen = text.length();
             }
            if (file_elider != null) {
               file_elider.noteEdit(off,len,tlen);
             }
            
            for (String user : base_ids) {
               if (user.equals(bid)) continue;
               sendEditToBubbles(user,off,len,txt);
             }
            
            line_offsets.update(off,off+len,text);
          }
       }
      catch (BadLocationException e) { }
      
      chng = is_changed;
      is_changed = true;
      clearSymbols();
      ver = ++file_version;
      proto.sendMessage("textDocument/didChange",
            "textDocument",getTextDocumentId(),"contentChanges",changes);
    }
   finally {
      unlockFile();
    }
   
   LspBaseMain lsp = LspBaseMain.getLspMain();
   if (!chng) {
      IvyXmlWriter mxw = lsp.beginMessage("FILECHANGE");
      mxw.field("FILE",getPath());
      lsp.finishMessage(mxw);
    }
   AutoCompile ac = new AutoCompile(ver,bid);
   lsp.startTask(ac);
}


void edit(String bid,int tid,JSONArray jedits) throws LspBaseException
{
   List<LspBaseEdit> edits = new ArrayList<>();
   for (int i = 0; i < jedits.length(); ++i) {
      JSONObject jedit = jedits.getJSONObject(i);
      LspBaseEdit ed = new LspBaseEdit(this,jedit);
      edits.add(ed);
    }
   edit(bid,tid,edits);
}



/********************************************************************************/
/*										*/
/*	Handle indentation							*/
/*										*/
/********************************************************************************/

void computeIndent(String bid,int id,int offset,boolean split,IvyXmlWriter xw)
   throws LspBaseException
{
   LineCol lc = mapOffsetToLspLineColumn(offset);
   int lstart = mapLspLineToOffset(lc.getLine());
   int lend = mapLspLineToOffset(lc.getLine()+1);
   Segment seg = getSegment(lstart,lend-lstart);
   int curwhite = computeVisualLength(seg);

   LspBaseProtocol lbp = getProject().getProtocol();
   JSONObject opts = createJson("tabSize",getTabSize(),"insertSpaces",true,
	 "trimTrailingWhitespace",false,"insertFinalNewLines",false);

   int newindent = curwhite;
   boolean exact = false;
   if (split) {
      LspBaseIndenter ind = new LspBaseIndenter(this,offset);
      newindent = ind.computePartialIndent();
    }
   else {
      IndentChecker ic = new IndentChecker(lstart,lend,curwhite);
      lbp.sendWorkMessage("textDocument/rangeFormatting",ic,
	    "textDocument",getTextDocumentId(),
	    "range",lbp.createRange(this,lstart-1,lend-1),
	    "options",opts);
      newindent = ic.getTargetIndent();
      if (newindent < 0) {
	 LspBaseIndenter ind = new LspBaseIndenter(this,lstart);
	 newindent = ind.computeIndent();
       }
      else exact = true;
    }

   xw.begin("INDENT");
   xw.field("LINE",lc.getLine()+1);
   xw.field("OFFSET",offset);
   xw.field("LINEOFFSET",lstart);
   xw.field("CURRENT",curwhite);
   xw.field("TARGET",newindent);
   xw.field("EXACT",exact);
   xw.end("INDENT");
}




protected int computeVisualLength(CharSequence indent)
{
   int tabsize = getTabSize();
   int length = 0;
   for (int i = 0; i < indent.length(); i++) {
      char ch = indent.charAt(i);
      switch (ch) {
	 case '\t':
	    if (tabsize > 0) {
	       int reminder = length % tabsize;
	       length += tabsize - reminder;
	     }
	    break;
	 case ' ':
	    length++;
	    break;
	 default :
	    return length;
       }
    }

   return length;
}



private class IndentChecker implements LspArrayResponder {

   private int line_start;
   private int line_end;
   private Integer add_indent;
   private int cur_white;

   IndentChecker(int lstart,int lend,int curwhite) {
      line_start = lstart;
      line_end = lend;
      add_indent = null;
      cur_white = curwhite;
    }

   int getTargetIndent() {
      if (add_indent == null) return -100;
      return cur_white + add_indent;
    }

   @Override public void handleResponse(JSONArray jarr) {
      add_indent = 0;		// for the case with no edits
      for (int i = 0; i < jarr.length(); ++i) {
	 JSONObject ed = jarr.getJSONObject(i);
	 String txt = ed.optString("newText");
	 if (txt.length() == 0) txt = null;
	 JSONObject range = ed.getJSONObject("range");
	 int lstart = mapRangeToStartOffset(range);
	 int lend = mapRangeToEndOffset(range);
	
	 if (txt != null && txt.length() >= 1 && txt.charAt(0) == '\n') {
	    if (line_start > lstart && line_start <= lend && lend < line_end) {
	       add_indent = txt.length() - (lend-lstart);
	     }
	  }
	 else if (txt == null && lstart < line_start + cur_white &&
	       lend <= line_start + cur_white) {
	    add_indent = -(lend-lstart);
	  }
       }
    }
}



/********************************************************************************/
/*										*/
/*	FIXINDENTS command							*/
/*										*/
/********************************************************************************/

void fixIndents(String bif,int id,int offset,IvyXmlWriter xw)
   throws LspBaseException
{
   IndentFixer fixer = new IndentFixer();

   LspBaseProtocol lbp = getProject().getProtocol();
   JSONObject opts = createJson("tabSize",getTabSize(),"insertSpaces",true,
	 "trimTrailingWhitespace",false,"insertFinalNewline",false);

   lbp.sendMessage("textDocument/onTypeFormatting",
	 fixer,
	 "textDocument",getTextDocumentId(),
	 "position",lbp.createPosition(this,offset),
	 "ch","\n",
	 "options",opts);

   JSONArray edits = fixer.getIndentEdits();
   if (edits == null) return;
   List<LspBaseEdit> editlist = new ArrayList<>();
   for (int i = 0; i < edits.length(); ++i) {
      LspBaseEdit edi = new LspBaseEdit(this,edits.getJSONObject(i));
      editlist.add(edi);
    }
   // do the edits, but tell clients to make them themselves
   edit("*INDENTS*",file_version,editlist);
}




private class IndentFixer implements LspArrayResponder {

   private JSONArray indent_edits;
   
   IndentFixer() {
      indent_edits = new JSONArray();
    }
   
   JSONArray getIndentEdits() {
      if (indent_edits.length() == 0) return null;
      return indent_edits;
    }
   
   @Override public void handleResponse(JSONArray edits) {
      for (int i = 0; i < edits.length(); ++i) {
         JSONObject edit = edits.getJSONObject(i);
         String txt = edit.optString("newText");
         if (txt.length() == 0) txt = null;
         JSONObject range = edit.getJSONObject("range");
         LineCol startlc = mapRangeToStartLspLineCol(range);
         LineCol endlc = mapRangeToEndLspLineCol(range);
         if (txt != null && txt.length() >= 1 && txt.charAt(0) == '\n') {
            if (startlc.getLine()+1 == endlc.getLine() && txt.trim().equals("")) {
               indent_edits.put(edit);
             }
          }
         else if (txt == null && startlc.getLine() == endlc.getLine()) {
            int lstart = mapLspLineToOffset(startlc.getLine());
            int lend = mapRangeToEndOffset(range);
            String repl = getText(lstart,lend-lstart);
            if (repl.trim().equals("")) {
               indent_edits.put(edit);
             }
          }
       }
    }

}       // end of inner class IndentFixer




/********************************************************************************/
/*                                                                              */
/*      FORMATCODE                                                              */
/*                                                                              */
/********************************************************************************/

void formatCode(String bid,int soffset,int eoffset,IvyXmlWriter xw)
      throws LspBaseException
{
   LspBaseProtocol proto = getProject().getProtocol();
   JSONObject opts = createJson("tabSize",getTabSize(),
         "insertSpaces",true,"trimTrailingWhiteSpace",true,
         "insertFinalNewLine",false,"trimFInalNewlines",true);
   FormatFixer fixer = new FormatFixer();
   if (soffset == 0 || eoffset <= soffset) {
      proto.sendMessage("textDocument/formatting",fixer,
            "textDocument",getTextDocumentId(),
            "options",opts);
    }
   else {
      proto.sendMessage("textDocument/rangeFormatting",fixer,
            "textDocument",getTextDocumentId(),
            "range",proto.createRange(this,soffset,eoffset),
            "options",opts); 
    }
   if (!fixer.madeEdits()) throw new LspBaseException("Problem with formatting");
}



private class FormatFixer implements LspArrayResponder {

   private boolean made_edits;
   
   FormatFixer() {
      made_edits = false;
    }
   
   boolean madeEdits()                  { return made_edits; }
   
   @Override public void handleResponse(JSONArray edits) {
      try {
         edit("*FORMAT",0,edits);
       }
      catch (LspBaseException e) {
         LspLog.logE("Problem with formatting edits",e);
       }
    }
   
}       // end of inner class FormatFixer



/********************************************************************************/
/*										*/
/*	Handle find regions							*/
/*										*/
/********************************************************************************/

void findRegions(String cls,boolean pfx,boolean stat,boolean compunit,
      boolean imports,boolean pkg,boolean topdecls,boolean fields,boolean all,
      IvyXmlWriter xw)
   throws LspBaseException
{
   JSONObject top = null;
   JSONArray syms = getSymbols();
   if (cls != null) {
      top = findSymbol(syms,cls);
      if (top == null) throw new LspBaseException("Class " + cls + " not found");
      syms = top.optJSONArray("nested");
    }
   else if (syms.length() == 1) {
      JSONObject sym = syms.getJSONObject(0);
      switch (SymbolKinds[sym.getInt("kind")]) {
	 case "Class" :
	 case "Interface" :
	 case "Enum" :
	 case "Struct" :
	    top = sym;
	    syms = top.optJSONArray("nested");
	    break;
       }
    }
   if (syms == null) syms = new JSONArray();

   int soffset = 0;
   int eoffset = getLength();
   if (top != null) {
      JSONObject rng = top.getJSONObject("range");
      soffset = mapRangeToStartOffset(rng);
      eoffset = mapRangeToEndOffset(rng);
    }

   int upto = eoffset;
   int restart = -1;
   for (int i = 0; i < syms.length(); ++i) {
      JSONObject sym = syms.getJSONObject(i);
      JSONObject rng = sym.getJSONObject("range");
      int symoff = mapRangeToStartOffset(rng);
      upto = Math.min(symoff,upto);
      int esymoff = mapRangeToEndOffset(rng);
      restart = Math.max(restart,esymoff);
    }

   boolean needtokens = compunit || imports || pkg;

   // prefix:  Everything from soffset to first symbol + last symbol to eoffset
   // statics: Static initializers -- probably none for dart
   // compunit:  first keyword to end of file
   // imports:	any line starting with an import keyword -- ranges only
   // package: any line starting with library/package keyword -- range only
   // topdelcs:  if there is only one top decl which has children, use it
   // fields:  All symbols that are fields -- output ranges only
   // all -- equal to topdecls unless there is only one top decl, a class

   // probably can assume that imports/package occur before first definition and
   //	limit tokens to that range
   TokenHolder th = new TokenHolder();
   if (needtokens) {
      setupTokens();
      LspBaseProtocol proto = getProject().getProtocol();
      proto.sendWorkMessage("textDocument/semanticTokens/range",th,
	    "textDocument",getTextDocumentId(),
	    "range",proto.createRange(this,soffset,upto));
    }

   if (compunit) {
      outputRange(0,getLength(),xw);
    }

   if (pfx) {
      if (restart < 0) {
	 outputRange(soffset,eoffset,xw);
       }
      else {
	 outputRange(soffset,upto-1,xw);
	 outputRange(restart+1,eoffset,xw);
       }
    }

   if (pkg) {
      for (Integer ln : th.getPackageLines()) {
	 int soff = mapLspLineToOffset(ln);
	 int eoff = mapLspLineToOffset(ln+1)-1;
	 outputRange(soff,eoff,xw);
       }
    }
   if (imports) {
      for (Integer ln : th.getImportLines()) {
	 int soff = mapLspLineToOffset(ln);
	 int eoff = mapLspLineToOffset(ln+1)-1;
	 outputRange(soff,eoff,xw);
       }
    }

   if (topdecls && !all) {
      if (restart < 0) {
	 outputRange(soffset,eoffset,xw);
       }
      else {
	 outputRange(upto,restart,xw);
       }
    }

   if (stat || all) {
      // find static initilizers -- not found in dart
      // need to find top level statements that are not declarations
    }

   if (fields) {
      for (int i = 0; i < syms.length(); ++i) {
	 JSONObject sym = syms.getJSONObject(i);
	 switch (SymbolKinds[sym.getInt("kind")]) {
	    case "Field" :
	    case "Variable" :
	    case "Constant" :
	    case "EnumMember" :
	       JSONObject rng = sym.getJSONObject("range");
	       int soff = mapRangeToStartOffset(rng);
	       int eoff = mapRangeToEndOffset(rng);
	       outputRange(soff,eoff,xw);
	       break;
	    default :
	       break;
	  }
       }
    }

   if (all) {
      for (int i = 0; i < syms.length(); ++i) {
	 JSONObject sym = syms.getJSONObject(i);
	 LspBaseUtil.outputLspSymbol(getProject(),this,sym,xw);
       }
    }
   if (all && topdecls) {
      if (top != null) {
	 LspBaseUtil.outputLspSymbol(getProject(),this,top,xw);
       }
    }
}


private void outputRange(int soffset,int eoffset,IvyXmlWriter xw)
{
   if (soffset == eoffset) return;

   xw.begin("RANGE");
   xw.field("PATH",getPath());
   xw.field("START",soffset);
   xw.field("END",eoffset);
   xw.end("RANGE");
}



private class TokenHolder implements LspJsonResponder {

   private List<Integer> package_lines;
   private List<Integer> import_lines;

   TokenHolder() {
      package_lines = new ArrayList<>();
      import_lines = new ArrayList<>();
    }

   List<Integer> getPackageLines()			{ return package_lines; }
   List<Integer> getImportLines()			{ return import_lines; }

   @Override public void handleResponse(JSONObject data) {
      JSONArray arr = data.getJSONArray("data");

      int line = 0;
      int col = 0;
      int lastline = -1;
      for (int i = 0; i < arr.length(); i += 5) {
	 int dline = arr.getInt(i+0);
	 line += dline;
	 if (dline > 0) col = 0;
	 col += arr.getInt(i+1);
	 int len = arr.getInt(i+2);
	 int soff = mapLspLineCharToOffset(line,col);
	 int eoff = soff + len;
	
	 String typ = token_types[arr.getInt(i+3)];
	 String cnt = getText(soff,eoff-soff);
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
	 LspLog.logD("REGION-TOKEN " + line + " " + col + " " + len + " " + typ + " " + mods + " " + cnt);
	
	 if (line != lastline && (typ == "keyword" || typ == "function")) {
	    switch (cnt) {
	       case "import" :
	       case "require" :
	       case "include" :
		  import_lines.add(line);
		  break;
	       case "package" :
	       case "module" :
	       case "library" :
		  package_lines.add(line);
		  break;
	     }
	  }
	 lastline = line;
       }
    }
}


/********************************************************************************/
/*										*/
/*	Handle commit								*/
/*										*/
/********************************************************************************/

boolean commit(boolean refresh,boolean save,boolean compile)
   throws Exception
{
   if (compile) {
      LspBaseProtocol lbp = for_project.getProtocol();
      LspBaseLanguageData ld = getLanguageData();
      if (ld.getCapability("diagnosticProvider") != null) {
	 lbp.sendMessage("textDocument/diagnostic",
	       "textDocument",getTextDocumentId());
       }
      else {
	 for_project.closeFile(this);
	 for_project.openFile(this);
       }
    }
   if (refresh) {
      refreshFile();
    }
   else if (file_version >= 0) {
      for_project.willSaveFile(this);
      try (FileWriter fw = new FileWriter(getFile())) {
	 fw.write(getContents());
       }
      catch (IOException e) {
	 LspLog.logE("Problem writing file",e);
       }
      for_project.saveFile(this);
    }
   return false;
}



/********************************************************************************/
/*										*/
/*	Position information							*/
/*										*/
/********************************************************************************/

Position createPosition(int offset) 
{
   if (file_version <= 0) {
      try {
         open(null);
       }
      catch (LspBaseException e) { 
         LspLog.logE("Problem opening file to create position",e);
       }
    }
   try {
      return file_contents.createPosition(offset);
    }
   catch (BadLocationException e) {
      return null;
    }
}


/********************************************************************************/
/*										*/
/*	Auto compile/elide							*/
/*										*/
/********************************************************************************/

private class AutoCompile implements Runnable {

   private int edit_version;
   private String bubbles_id;

   AutoCompile(int version,String bid) {
      edit_version = version;
      bubbles_id = bid;
    }

   @Override public void run() {
      int delay = getProject().getDelayTime(bubbles_id);
      if (delay < 0) return;
      if (file_version != edit_version) return;
      try {
	 Thread.sleep(delay);
       }
      catch (InterruptedException e) { }
      if (file_version != edit_version) return;

      // get messages for file if that is necessary -- might be automatic

      if (file_version != edit_version) return;
      if (getProject().getAutoElide(bubbles_id) && file_elider != null) {
	 LspBaseMain lsp = LspBaseMain.getLspMain();
	 IvyXmlWriter xw = lsp.beginMessage("ELISION",bubbles_id);
	 xw.field("FILE",for_file.getPath());
	 xw.field("ID",edit_version);
	 xw.begin("ELISION");
         try {
            if (file_elider.computeElision(xw)) {
               if (file_version == edit_version) {
                  xw.end("ELISION");
                  lsp.finishMessage(xw);
                }
             }
          }
         catch (LspBaseException e) {
            LspLog.logE("Problem computing elision",e);
          }
       }
    }

}	// end of inner class AutoCompile



/********************************************************************************/
/*										*/
/*	Handle GETCOMPLETIONS							*/
/*										*/
/********************************************************************************/

void getCompletions(String bid,int offset,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseProtocol proto = for_project.getProtocol();
   proto.sendWorkMessage("textDocument/completion",
	 new CompletionHandler(xw),
	 "textDocument",getTextDocumentId(),
	 "position",proto.createPosition(this,offset));
}



private class CompletionHandler implements LspJsonResponder {

   private IvyXmlWriter xml_writer;

   CompletionHandler(IvyXmlWriter xw) {
      xml_writer = xw;
    }

   @Override public void handleResponse(JSONObject jdata) {
      JSONArray items = jdata.getJSONArray("items");
      List<CompletionItem> result = new ArrayList<>();
      for (int i = 0; i < items.length(); ++i) {
	 JSONObject item = items.getJSONObject(i);
	 CompletionItem itm = new CompletionItem(item);
	 if (itm.isUsable()) result.add(itm);
       }
      Collections.sort(result);
      xml_writer.begin("COMPLETIONS");
      for (CompletionItem itm : result) {
	 itm.outputXml(xml_writer);
       }
      xml_writer.end("COMPLETIONS");
    }

}	// end of inner class CompletionHandler


private class CompletionItem implements Comparable<CompletionItem> {

   private String completion_kind;
   private String completion_name;
   private String completion_text;
   private String signature_text;
   private int start_offset;
   private int end_offset;
   private String sort_on;

   CompletionItem(JSONObject itm) {
      start_offset = -1;
      completion_kind = CompletionKinds[itm.optInt("kind")];
      JSONObject edit = itm.optJSONObject("textEdit");
      if (edit == null) return;

      JSONObject repl = edit.optJSONObject("replace");
      int rstart = mapRangeToStartOffset(repl);
      int rend = mapRangeToEndOffset(repl);
      JSONObject insert = edit.optJSONObject("insert");
      int istart = mapRangeToStartOffset(insert);
      int iend = mapRangeToEndOffset(insert);
      if (istart != iend || istart != rstart) {
	 LspLog.logE("BAD INSERT/REPLACE EDIT " + itm.toString(2));
	 return;
       }
      start_offset = rstart;
      end_offset = rend;

      completion_text = edit.getString("newText");
      completion_name = itm.getString("label");
      completion_name = completion_name.replace("\u2026","...");
      signature_text = itm.optString("detail",null);
      if (signature_text != null) {
	 signature_text = signature_text.replace("\u2192","->");
       }
      sort_on = itm.optString("sortText","");
      sort_on += "_" + completion_name;

    }

   boolean isUsable() {
      if (start_offset < 0) return false;
      return true;
    }

   void outputXml(IvyXmlWriter xw) {
      xw.begin("COMPLETION");
      xw.field("KIND",completion_kind);
      xw.field("NAME",completion_name);
      if (signature_text != null) xw.field("SIGNATURE",signature_text);
      xw.field("TEXT",completion_text);
      xw.field("REPLACE_START",start_offset);
      xw.field("REPLACE_END",end_offset);
      xw.field("RELEVANCE",1);
      xw.end("COMPLETION");
    }

   @Override public int compareTo(CompletionItem ci) {
      return sort_on.compareTo(ci.sort_on);
    }

}	// end of inner class CompletionItem




/********************************************************************************/
/*										*/
/*	Handle Quick Fix requests						*/
/*										*/
/********************************************************************************/

void getCodeActions(String bid,int offset,int length,List<Element> problems,
      IvyXmlWriter xw)
   throws LspBaseException
{
   if (offset < 0) return;

   JSONArray diags = new JSONArray();
   for (Element prob : problems) {
      LspLog.logD("HANDLE PROBLEM " + IvyXml.convertXmlToString(prob));
      // convert prob to JSON object and add to diags
    }

   JSONObject ctx = createJson("diagnostics",diags,
	 "only",createJsonArray("quickfix"),
	 "triggerKind",1);

   LspBaseProtocol proto = for_project.getProtocol();
   CodeActions cact = new CodeActions(xw);
   proto.sendMessage("textDocument/codeAction",cact,
	 "textDocument",getTextDocumentId(),
	 "range",proto.createRange(this,offset,offset+length),
	 "context",ctx);
}


private class CodeActions implements LspArrayResponder {

   private IvyXmlWriter xml_writer;

   CodeActions(IvyXmlWriter xw) {
      xml_writer = xw;
    }

   @Override public void handleResponse(JSONArray cacts) {
      int len = cacts.length();
      for (int i = 0; i < len; ++i) {
         JSONObject cact = cacts.getJSONObject(i);
         String kind = cact.optString("kind",null);
         if (kind == null) continue;
         if (kind.equals("quickfix.create.method")) continue;
         if (cact.optJSONObject("disabled") != null) continue;
         if (cact.optJSONObject("command") != null) continue;
         JSONObject edit = cact.optJSONObject("edit");
         if (!isValidEdit(edit)) continue;
         
         int rel = (len - i)*10;
         if (cact.optBoolean("isPreferred")) rel += len*10;
         
         xml_writer.begin("FIX");
         xml_writer.field("RELEVANCE",rel);
         xml_writer.field("DISPLAY",cact.getString("title"));
         xml_writer.field("ID",cact.hashCode());
         JSONArray edits = edit.getJSONArray("documentChanges");
         JSONArray editl = edits.getJSONObject(0).getJSONArray("edits");
         LspBaseUtil.outputTextEdit(LspBaseFile.this,editl,xml_writer);
         xml_writer.end("FIX");
       }
    }

   private boolean isValidEdit(JSONObject edit) {
      if (edit == null) return false;
      JSONArray changes = edit.optJSONArray("documentChanges");
      if (changes == null || changes.length() == 0) return false;
      for (int i = 0; i < changes.length(); ++i) {
         JSONObject chng = changes.getJSONObject(i);
         String kind = chng.optString("kind",null);
         LspLog.logD("CHANGE KIND " + kind);
         // only allow edits for now
         if (kind != null) return false;
         JSONObject file = chng.getJSONObject("textDocument");
         String uri = file.getString("uri");
         LspLog.logD("MATCH URI " + uri + " " + getUri());
         if (!uri.equals(getUri())) return false;
       }
      return true;
    }

}	// end of inner class CodeActions



/********************************************************************************/
/*										*/
/*	Handle FIXIMPORTS							*/
/*										*/
/********************************************************************************/

void fixImports(String bid,int demand,int staticdemand,String order,
      String add,IvyXmlWriter xw)
   throws LspBaseException
{
   JSONArray diags = new JSONArray();
   JSONObject ctx = createJson("diagnostics",diags,
	 "only",createJsonArray("source.organizeImports"),
	 "triggerKind",1);

   LspBaseProtocol proto = for_project.getProtocol();
   ImmediateActions cact = new ImmediateActions();
   proto.sendMessage("textDocument/codeAction",cact,
	 "textDocument",getTextDocumentId(),
	 "range",proto.createRange(this,0,getLength()),
	 "context",ctx);
}



private class ImmediateActions implements LspArrayResponder {

   ImmediateActions() { }
   
   @Override public void handleResponse(JSONArray cacts) {
      LspBaseMain lsp = LspBaseMain.getLspMain();
      LspBaseProjectManager lpm = lsp.getProjectManager();
      int len = cacts.length();
      for (int i = 0; i < len; ++i) {
         JSONObject cact = cacts.getJSONObject(i);
         String kind = cact.optString("kind",null);
         if (kind == null) continue;
         if (kind.equals("quickfix.create.method")) continue;
         if (cact.optJSONObject("disabled") != null) continue;
         if (cact.optJSONObject("command") != null) continue;
         JSONObject wsedit = cact.optJSONObject("edit");
         try {
            lpm.applyWorkspaceEdit(wsedit);
          }
         catch (LspBaseException e) {
            LspLog.logE("Problem with immediate action edit",e);
          }
       }
    }
   
}	// end of inner class ImmediateActions



/********************************************************************************/
/*										*/
/*	Handle RENAME								*/
/*										*/
/********************************************************************************/

void rename(int soffset,int eoffset,String name,String handle,String newname,
      boolean keeporig,boolean getters,boolean setters,boolean hier,
      boolean qualified,boolean refs,boolean similar,boolean text,
      boolean doedit,String files,IvyXmlWriter xw)
{
   LspBaseProtocol proto = for_project.getProtocol();
   JSONObject pos = proto.createPosition(this,soffset);
   Renamer renamer = new Renamer(xw);
   try {
      proto.sendWorkMessage("textDocument/rename",renamer,
            "textDocument",getTextDocumentId(),
            "position",pos,
            "newName", newname);
    }
   catch (LspBaseException e) {
      xw.begin("FAILURE");
      xw.field("TYPE","ERROR");
      xw.field("MESSAGE",e.getMessage());
      xw.end("FAILURE");
    }
}


private class Renamer implements LspJsonResponder {

   private IvyXmlWriter xml_writer;

   Renamer(IvyXmlWriter xw) {
      xml_writer = xw;
    }

   @Override public void handleResponse(JSONObject wsedit) {
      LspBaseMain lsp = LspBaseMain.getLspMain();
      LspBaseProjectManager lpm = lsp.getProjectManager();
      if (wsedit.isNull("documentChanges")) {
         xml_writer.begin("FAILURE");
         xml_writer.field("TYPE","NOCHANGE");
         xml_writer.end("FAILURE");
       }
      else {
         try {
            lpm.applyWorkspaceEdit(wsedit);
          }
         catch (LspBaseException e) {
            LspLog.logE("Problem applying edits",e);
          }
         xml_writer.emptyElement("EDITS");
       }
    }


}	// end of inner class Renamer



/********************************************************************************/
/*										*/
/*	Handle Private Buffers							*/
/*										*/
/********************************************************************************/

void createPrivateBuffer(String bid,String pid,String frompid,IvyXmlWriter xw)
      throws LspBaseException
{
   if (pid == null) {
      for (int i = 0; i < 100; ++i) {
	 int v = (int)(Math.random() * 10000000);
	 pid = "pid_" + v;
	 if (private_buffers.get(pid) == null) break;
       }
    }
   else if (private_buffers.get(pid) != null) {
      throw new LspBaseException("Buffer id " + pid + " already used");
    }

   PrivateBuffer pbf = new PrivateBuffer(pid,frompid);
   private_buffers.put(pid,pbf);
   private_buffers.put(pbf.getPrivateUri(),pbf);

   CreatePrivateBufferTask task = new CreatePrivateBufferTask(pid);
   LspBaseMain lspbase = LspBaseMain.getLspMain();
   lspbase.startTask(task);

   xw.text(pid);
}


private class CreatePrivateBufferTask implements Runnable {

   private String buffer_pid;

   CreatePrivateBufferTask(String pid) {
      buffer_pid = pid;
    }

   @Override public void run() {
      LspBaseProtocol proto = for_project.getProtocol();
      PrivateBuffer pbf = private_buffers.get(buffer_pid);
      if (pbf == null) return;
      JSONObject docitm = createJson("uri",pbf.getPrivateUri(),"languageId",file_language,
            "version",pbf.getVersion(),"text",pbf.getBufferContents());
      try {
         proto.sendMessage("textDocument/didOpen","textDocument",docitm);
       }
      catch (LspBaseException e) {
         LspLog.logE("Problem opening private buffer",e);
       }
    }

}


void editPrivateBuffer(String pid,List<LspBaseEdit> edits,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseProtocol proto = for_project.getProtocol();

   PrivateBuffer pbf = findPrivateBuffer(pid);
   if (pbf == null) throw new LspBaseException("Private buffer " + pid + " not found");

   JSONArray changes = new JSONArray();
   for (LspBaseEdit edit : edits) {
      int len = edit.getLength();
      int off = edit.getOffset();
      String text = edit.getText();

      String txt = (text == null ? "" : text);
      JSONObject rng = proto.createRange(this,off,off+len);
      JSONObject chng = createJson("range",rng,"text",txt);
      changes.put(chng);
    }
   JSONObject docitm1 = createJson("uri",pbf.getPrivateUri(),"version",pbf.noteEdit());
   proto.sendMessage("textDocument/didChange","textDocument",docitm1,
	 "contentChanges",changes);
}



void removePrivateBuffer(String pid) throws LspBaseException

{
   PrivateBuffer pbf = findPrivateBuffer(pid);
   if (pbf != null) {
      LspBaseProtocol proto = for_project.getProtocol();     JSONObject docitm1 = createJson("uri",pbf.getPrivateUri());
      proto.sendMessage("textDocument/didClose","textDocument",docitm1);
      private_buffers.remove(pid);
      private_buffers.remove(pbf.getPrivateUri());
    }
}


private PrivateBuffer findPrivateBuffer(String pid)
{
   PrivateBuffer pbf = private_buffers.get(pid);
   return pbf;
}





private class PrivateBuffer {

   private GapContent buffer_text;
   private String buffer_name;
   private File file_name;
   private int version_id;
   

   PrivateBuffer(String pid,String frompid) throws LspBaseException {
      buffer_name = pid;
      buffer_text = new GapContent();
      String txt = null;
      if (frompid == null) txt = getContents();
      else {
         PrivateBuffer pbf = findPrivateBuffer(frompid);
         if (pbf == null) throw new LspBaseException("Private buffer " + pbf + " not found");
         txt = pbf.getBufferContents();
       }
      LspLog.logD("PRIVATE BUFFER CONTENTS: " + txt);
      try {
         buffer_text.insertString(0,txt);
       }
      catch (BadLocationException e) { }
      String fnm = getPath();
      int idx = fnm.lastIndexOf(".");
      String hdr = fnm.substring(0,idx);
      String pvtnm = hdr + PRIVATE_PREFIX + buffer_name + fnm.substring(idx);
      file_name = new File(pvtnm);
      version_id = 1;
    }

   String getPrivateUri()		{ return getUri(file_name); }
   
   int getVersion()                     { return version_id; }
   int noteEdit()                       { return ++version_id; }

   String getBufferContents() {
      try {
         return buffer_text.getString(0,buffer_text.length());
       }
      catch (BadLocationException e) {
         LspLog.logE("Problem getting buffer contents " + e);
       }
      return "";
    }

}	// end of inner class PrivateBuffer



}	// end of class LspBaseFile




/* end of LspBaseFile.java */

