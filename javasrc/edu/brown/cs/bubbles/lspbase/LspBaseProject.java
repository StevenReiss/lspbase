/********************************************************************************/
/*                                                                              */
/*              LspBaseProject.java                                             */
/*                                                                              */
/*      description of class                                                    */
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
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseProject implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private LspBaseMain     lsp_base;
private LspBaseProjectManager project_manager;
private String          project_name;
private String          project_language;
private File            base_directory;
private List<LspBasePathSpec> project_paths;
private Map<String,LspBaseFile> file_map;
private List<LspBaseFile> project_files;
private boolean         is_open;
private LspBaseProtocol use_protocol;
private LspBasePreferences project_preferences;
private Map<String,EditParameters> edit_parameters;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseProject(LspBaseMain lm,LspBaseProjectManager pm,String name,File base) 
{
   lsp_base = lm;
   project_manager = pm;
   base_directory = IvyFile.getCanonical(base);
   if (name == null) name = base.getName();
   project_name = name;
   project_paths = new ArrayList<>();
   project_files = new ArrayList<>();
   file_map = new HashMap<>();
   project_preferences = new LspBasePreferences(pm.getSystemPreferences());
   edit_parameters = new HashMap<>();
   
   File f = new File(base_directory,".bubbles");
   if (!f.exists()) f.mkdir();
   
   File f1 = new File(base_directory,PROJECT_DATA_FILE);
   Element xml = IvyXml.loadXmlFromFile(f1);
   if (xml != null) {
      project_language = IvyXml.getAttrString(xml,"LANGUAGE","dart");
      for (Element pe : IvyXml.children(xml,"PATH")) {
	 LspBasePathSpec ps = project_manager.createPathSpec(pe);
	 project_paths.add(ps);
       }
      for (Element fe : IvyXml.children(xml,"FILE")) {
	 String nm = IvyXml.getTextElement(fe,"NAME");
	 File fs = new File(nm);
         addLspFile(fs,false);
       }
      project_preferences.loadXml(xml);
    }
   is_open = false;
   use_protocol = null;
// use_protocol = lsp_base.findProtocol(base_directory,project_language);
   saveProject();
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

String getName() 			        { return project_name; }
String getLanguage()                            { return project_language; }
LspBaseProtocol getProtocol()                   { return use_protocol; }
File getBasePath()				{ return base_directory; }
// NobasePreferences getPreferences()		{ return nobase_prefs; }
boolean isOpen() 			        { return is_open; }
boolean exists() 			        { return base_directory.exists(); }
LspBaseProject [] getReferencedProjects()       { return new LspBaseProject[0]; }
LspBaseProject [] getReferencingProjects()      { return new LspBaseProject[0]; }

LspBasePreferences getPreferences()             { return project_preferences; }

LspBaseFile findFile(String path)
{
   return file_map.get(path);
}

LspBaseFile findFile(File f)
{
   return findFile(f.getAbsolutePath());
}

String getRelativeFile(LspBaseFile f)
{
   String p0 = IvyFile.getCanonicalPath(f.getFile());
   
   for (LspBasePathSpec ps : project_paths) {
      if (ps.isUser() && !ps.isExclude()) {
         File dir = ps.getFile();
         File dir1 = getUserSourceDirectory(dir);
         if (dir1 == null) continue;
         String p1 = IvyFile.getCanonicalPath(dir1);
         if (p0.startsWith(p1)) {
            String p2 = p0.substring(p1.length()+1);
            return p2;
          }
       }
    }
      
   return null;
}



/********************************************************************************/
/*                                                                              */
/*      Open methods                                                            */
/*                                                                              */
/********************************************************************************/

void open()
{
   if (is_open) return;
   
   if (use_protocol == null) {
      use_protocol = lsp_base.findProtocol(base_directory,project_language,project_paths);
    }
   
   for (LspBasePathSpec ps : project_paths) {
      if (ps.isUser() && !ps.isExclude()) {
         File dir = ps.getFile();
         File dir1 = getUserSourceDirectory(dir);
         if (dir1 != null) {
            findFiles(null,dir1,false);
          }
       }
    }
   
   use_protocol.initialize();
   
   is_open = true;
}




private File getUserSourceDirectory(File dir)
{
   for (File f1 = dir; f1 != null; f1 = f1.getParentFile()) {
      File fyaml = new File(f1,"pubspec.yaml");
      File flib = new File(f1,"lib");
      if (fyaml.exists() && flib.exists() && flib.isDirectory()) {
         return flib;
       }
    }
   
   return null;
}



protected void findFiles(String pfx,File f,boolean reload)
{
   boolean nest = true;
   for (LspBasePathSpec ps : project_paths) {
      if (!ps.isUser() || ps.isExclude()) {
         if (ps.match(f)) return;
       }
      if (!ps.isNested()) {
         if (ps.match(f)) nest = false;
       }
    }
   
   FileFilter filter = lsp_base.getLanguageData(project_language).getSourceFilter();
   
   if (f.isDirectory()) {
      File [] fls = f.listFiles(filter);
      String npfx = null;
      if (pfx != null) npfx = pfx + "." + f.getName();
      for (File f1 : fls) {
         if (!nest && f1.isDirectory()) continue;
         findFiles(npfx,f1,reload);
       }
      return;
    }
   
   if (!filter.accept(f)) return;
   addLspFile(f,reload);
}


private LspBaseFile addLspFile(File file,boolean reload)
{
   LspBaseFile lbf0 = findFile(file);
   if (lbf0 != null) {
      if (reload) lbf0.reload();
      return lbf0;
    }
   
   LspLog.logD("Add File " + file.getAbsolutePath());
   
   LspBaseFile lbf = new LspBaseFile(this,file,project_language);
   project_files.add(lbf);
   file_map.put(file.getAbsolutePath(),lbf);
   try {
      file_map.put(file.getCanonicalPath(),lbf);
    }
   catch (IOException e) { }
   
   return lbf;
}




/********************************************************************************/
/*                                                                              */
/*      Names methods                                                           */
/*                                                                              */
/********************************************************************************/

void getAllNames(LspNamer namer)
{
   for (LspBaseFile lbf : project_files) {
      JSONObject tdi = createJson("uri",lbf.getUri());
      use_protocol.sendMessage("textDocument/documentSymbol",
            new NameHandler(namer,this,lbf),
            "textDocument",tdi);
    }
}


private class NameHandler implements LspResponder {

   private LspBaseProject for_project;
   private LspBaseFile for_file;
   private LspNamer use_namer;
   
   NameHandler(LspNamer namer,LspBaseProject project,LspBaseFile file) {
      for_project = project;
      for_file = file;
      use_namer = namer;
    }
   
   
   @Override public void handleResponse(Object resp,JSONObject err) {
      if (err != null) return;
      if (resp == null) return;
      JSONArray jarr = (JSONArray) resp;
      use_namer.handleNames(for_project,for_file,jarr);
    }
   
}       // end of inner class NameHandler




/********************************************************************************/
/*                                                                              */
/*      Build methods                                                           */
/*                                                                              */
/********************************************************************************/

void commit(String bid,boolean refresh,boolean save,List<Element> files,IvyXmlWriter xw)
{
   if (files == null || files.size() == 0) {
      for (LspBaseFile lbf : project_files) {
         if (refresh || !save || lbf.hasChanged()) {
            commitFile(lbf,bid,refresh,save,xw);
          }
       }
    }
   else {
      for (Element e : files) {
         String fnm = IvyXml.getAttrString(e,"NAME");
         LspBaseFile lbf = findFile(fnm);
         if (lbf != null) {
            boolean r = IvyXml.getAttrBool(e,"REFRESH",refresh);
            boolean s = IvyXml.getAttrBool(e,"SAVE",save);
            commitFile(lbf,bid,r,s,xw);
          }
       }
    }
}


private void commitFile(LspBaseFile lbf,String bid,boolean refresh,boolean save,IvyXmlWriter xw)
{
   boolean upd = false;
   
   lbf.lockFile(bid);
   try {
      xw.begin("FILE");
      xw.field("NAME",lbf.getPath());
      try {
         upd = lbf.commit(refresh,save);
       }
      catch (Throwable t) {
         xw.field("ERROR",t.toString());
       }
      xw.end("FILE");
    }
   finally {
      lbf.unlockFile();
    }
   if (upd) {
      // start autocompile
    } 
   
}


void build(boolean refresh,boolean reload)
{
   if (!is_open) {
      open();
      return;
    }
   
   Set<LspBaseFile> oldfiles = null;
   if (refresh) {
      oldfiles = new HashSet<>(project_files);
      if (reload) {
         project_files.clear();
       }
    }
   
   for (LspBasePathSpec ps : project_paths) {
      if (ps.isUser() && !ps.isExclude()) {
         File dir = ps.getFile();
         findFiles(null,dir,reload);
       }
    }
   
}


/********************************************************************************/
/*                                                                              */
/*      Search methods                                                          */
/*                                                                              */
/********************************************************************************/

void patternSearch(String pat,String typ,boolean defs,boolean refs,boolean system,IvyXmlWriter xw)
{
   //TODO: might need to modify pattern here
   SearchResult sr = new SearchResult();
   use_protocol.sendMessage("workspace/symbol",
         (Object data,JSONObject err) -> sr.addResult((JSONArray) data,err),
         "query",pat);
   
}



private class SearchResult {
   
   void addResult(JSONArray syms,JSONObject err) { 
      if (err != null) {
         LspLog.logD("Search Result Error " + err.toString(2));
       }
      else {
         LspLog.logD("Search Result " + syms.toString(2));
       }
    }
}



/********************************************************************************/
/*                                                                              */
/*      File methods                                                            */
/*                                                                              */
/********************************************************************************/

void openFile(LspBaseFile lbf)
{
   use_protocol.sendMessage("textDocument/didOpen",this::openFileResponse,
         "textDocument",lbf.getTextDocumentItem());
   
}


private void openFileResponse(Object resp,JSONObject err)
{
}



void editFile(LspBaseFile lbf,List<LspBaseEdit> edits)
{
   JSONArray changes = new JSONArray();
   for (LspBaseEdit ed : edits) {
      LineCol lc0 = lbf.mapOffsetToLineColumn(ed.getOffset());
      LineCol lc1 = lbf.mapOffsetToLineColumn(ed.getOffset() + ed.getLength());
      JSONObject rng = createJson("start",createJson("line",lc0.getLine(),"character",lc0.getColumn()),
            "end",createJson("line",lc1.getLine(),"character",lc1.getColumn()));
      JSONObject chng = createJson("range",rng,"text",ed.getText());
      changes.put(chng);
    }
   use_protocol.sendMessage("textDocument/didChange",null,
         "textDocument",lbf.getTextDocumentId(),"contentChanges",changes);
}

void willSaveFile(LspBaseFile lbf)
{
   use_protocol.sendMessage("textDocument/willSave",null,
         "textDocument",lbf.getTextDocumentId(),"reason",1);
         
}



void saveFile(LspBaseFile lbf)
{
   use_protocol.sendMessage("textDocument/didSave",null,
         "textDocument",lbf.getTextDocumentId());
}


void closeFile(LspBaseFile lbf)
{
   use_protocol.sendMessage("textDOcument/didClose",null,
         "textDocument",lbf.getTextDocumentId());
}





/********************************************************************************/
/*										*/
/*	Output methods								*/
/*										*/
/********************************************************************************/

void saveProject()
{
   try {
      File f1 = new File(base_directory,PROJECT_DATA_FILE);
      IvyXmlWriter xw = new IvyXmlWriter(f1);
      outputXml(xw);
      xw.close();
    }
   catch (IOException e) {
      LspLog.logE("Problem writing project file",e);
    }
}



void outputXml(IvyXmlWriter xw)
{
   xw.begin("PROJECT");
   xw.field("NAME",project_name);
   xw.field("LANGUAGE",project_language);
   xw.field("BASE",base_directory.getPath());
   for (LspBasePathSpec ps : project_paths) {
      ps.outputXml(xw);
    }
   for (LspBaseFile fd : project_files) {
      outputFile(fd,xw);
    }
// nobase_prefs.outputXml(xw);
   xw.end("PROJECT");
}




void outputProject(boolean files,boolean paths,boolean clss,boolean opts,IvyXmlWriter xw)
{
   if (xw == null) return;
   
   xw.begin("PROJECT");
   xw.field("NAME",project_name);
   xw.field("LANGUAGE",project_language);
   xw.field("PATH",base_directory.getPath());
   xw.field("WORKSPACE",project_manager.getWorkSpaceDirectory().getPath());
   
   if (paths) {
      xw.begin("CLASSPATH");
      for (LspBasePathSpec ps : project_paths) {
	 ps.outputXml(xw);
       }
      xw.end("CLASSPATH");
    }
   if (files) {
      for (LspBaseFile fd : project_files) {
	 outputFile(fd,xw);
       }
    }
   
// if (opts) nobase_prefs.outputXml(xw);
   
   xw.end("PROJECT");
}



private void outputFile(LspBaseFile fd,IvyXmlWriter xw)
{
   xw.begin("FILE");
   xw.field("NAME",fd.getPath());
   xw.field("LANGUAGE",fd.getLanguage());
   xw.end("FILE");
}



/********************************************************************************/
/*                                                                              */
/*      Edit parameters                                                         */
/*                                                                              */
/********************************************************************************/

void handleEditParameter(String bid,String name,String value)
{
   EditParameters ep = getParameters(bid);
   ep.setParameter(name,value);
}




private EditParameters getParameters(String id)
{
   synchronized (edit_parameters) {
      EditParameters ep = edit_parameters.get(id);
      if (ep == null) {
         ep = new EditParameters();
         edit_parameters.put(id,ep);
       }
      return ep;
    }
}



private static class EditParameters {

   private int delay_time;
   private boolean auto_elide;
   
   EditParameters() {
      delay_time = 250;
      auto_elide = false;
    }
   
   int getDelayTime()           { return delay_time; }
   boolean getAutoElide()       { return auto_elide; }
   
   void setParameter(String name,String value) {
      if (name.equals("AUTOELIDE")) {
         auto_elide = Boolean.parseBoolean(value);
       }
      else if (name.equals("ELIDEDELAY")) {
         delay_time = Integer.parseInt(value);
       }
    }

}       // end of inner class EditParamters






}       // end of class LspBaseProject




/* end of LspBaseProject.java */

