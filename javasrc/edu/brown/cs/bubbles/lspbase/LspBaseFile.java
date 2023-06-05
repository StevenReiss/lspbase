/********************************************************************************/
/*                                                                              */
/*              LspBaseFile.java                                                */
/*                                                                              */
/*      Information about a file                                                */
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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import javax.swing.text.BadLocationException;
import javax.swing.text.GapContent;
import javax.swing.text.Position;

import org.json.JSONObject;

import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseFile implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
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




/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
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
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

String getPath()                { return for_file.getPath(); }
File getFile()                  { return for_file; }
String getLanguage()            { return file_language; }
String getUri()                 { return getUri(for_file); }
LspBaseProject getProject()     { return for_project; }
boolean hasChanged()            { return is_changed; }

synchronized LspBaseElider getElider()
{
   if (file_elider == null) {
      file_elider = new LspBaseElider(this);
    }
   return file_elider;
}

String getContents() {
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
   try {
      return file_contents.getString(0,file_contents.length());
    }
   catch (BadLocationException e) {
      return "";
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
/*                                                                              */
/*      File locking                                                            */
/*                                                                              */
/********************************************************************************/

void lockFile(String bid)
{ }

void unlockFile()
{}


/********************************************************************************/
/*                                                                              */
/*      Line -- Position mapping (line/char are 1-based)                        */
/*                                                                              */
/********************************************************************************/

int mapLspLineToOffset(int line)
{
   return line_offsets.findOffset(line+1);
}

int mapLineToOffset(int line)
{
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
         line_offsets = new LspBaseLineOffsets(newline,
               new StringReader(file_contents.toString()));
       }
    }
}

/********************************************************************************/
/*                                                                              */
/*      Editing methods                                                         */
/*                                                                              */
/********************************************************************************/

void open()
{
   if (file_version > 0) return;
   
   getContents();
   
   for_project.openFile(this);
   file_version = 1;
} 


void close()
{
   for_project.closeFile(this);
   file_contents = null;
   file_version = -1;
}


void reload()
{ }
 

void edit(String bid,int id,List<LspBaseEdit> edits)
{
   // compare id with current version number 
   
   if (file_version <= 0) {
      open();
    }
   
   try {
      for (LspBaseEdit edit : edits) {
         int len = edit.getLength();
         int off = edit.getOffset();
         int tlen = 0;
         String text = edit.getText();
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
       }
    }
   catch (BadLocationException e) { }
   
   boolean chng = is_changed;
   is_changed = true;
   int ver = ++file_version;
   for_project.editFile(this,edits);
   
   LspBaseMain lsp = LspBaseMain.getLspMain();
   if (!chng) {
      IvyXmlWriter mxw = lsp.beginMessage("FILECHANGE");
      mxw.field("FILE",getPath());
      lsp.finishMessage(mxw);
    }
   AutoCompile ac = new AutoCompile(ver,bid);
   lsp.startTask(ac);
}


boolean commit(boolean refresh,boolean save) 
   throws Exception
{ 
   if (refresh) {
      close();
      open();
    }
   else if (file_version >= 0) {
      for_project.willSaveFile(this);
      // TODO: create backup file
      try (FileWriter fw = new FileWriter(getFile())) {
         fw.write(getContents());
       }
      catch (IOException e) {
         LspLog.logE("Problem writing file",e);
       }
      for_project.saveFile(this);
    }
   // send presave/save messages, etc.
   return false;
}
   


/********************************************************************************/
/*                                                                              */
/*      Position information                                                    */
/*                                                                              */
/********************************************************************************/

Position createPosition(int offset)
{
   if (file_version <= 0) {
      open();
    }
   try {
      return file_contents.createPosition(offset); 
    }
   catch (BadLocationException e) { 
      return null;
    }
}


/********************************************************************************/
/*                                                                              */
/*      Auto compile/elide                                                      */
/*                                                                              */
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
         if (file_elider.computeElision(xw)) {
            if (file_version == edit_version) {
               xw.end("ELISION");
               lsp.finishMessage(xw);
             }
          }
       }
    }
   
}       // end of inner class AutoCompile



}       // end of class LspBaseFile




/* end of LspBaseFile.java */

